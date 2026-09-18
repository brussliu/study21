#!/usr/bin/env node
/**
 * Study 2.1 — ブラウザ拡張の配布パッケージ（zip）を作る。
 *
 * deploy（`docker compose build web`）の中でフロントのビルド時に呼ばれ、
 * nginx が配る `/downloads/study21-extension.zip` として置かれる。
 * 「インターネット利用履歴 → Web閲覧履歴」タブの「ブラウザ拡張」カードの
 * ダウンロードボタンがこれを落とす（2.0 は手作業で zip を作って配っていた）。
 *
 *   使い方:
 *     node extension/build-zip.mjs
 *     node extension/build-zip.mjs --out frontend/pc-web/public/downloads/study21-extension.zip
 *
 * 依存パッケージを増やしたくないので、ZIP はここで組み立てる（圧縮は node:zlib）。
 * 出来た zip の中身は `study21-extension/` の下にまとめる（解凍すると 1 フォルダになる）。
 */
import { mkdirSync, readdirSync, readFileSync, statSync, writeFileSync } from 'node:fs'
import { dirname, join, relative, resolve, sep } from 'node:path'
import { fileURLToPath } from 'node:url'
import { deflateRawSync } from 'node:zlib'

/** 拡張機能のフォルダ（このスクリプトの隣）。 */
const EXTENSION_DIR = dirname(fileURLToPath(import.meta.url))
const REPO_ROOT = resolve(EXTENSION_DIR, '..')
/** zip の中のトップフォルダ名。 */
const ROOT_IN_ZIP = 'study21-extension'
const DEFAULT_OUT = join(REPO_ROOT, 'frontend/pc-web/public/downloads/study21-extension.zip')

function parseArgs(argv) {
  const options = { out: DEFAULT_OUT, json: null }
  for (let index = 0; index < argv.length; index += 1) {
    const arg = argv[index]
    if (arg === '--out') {
      options.out = resolve(argv[index + 1] ?? '')
      index += 1
    } else if (arg === '--json') {
      options.json = resolve(argv[index + 1] ?? '')
      index += 1
    } else if (arg === '--help' || arg === '-h') {
      console.log('使い方: node extension/build-zip.mjs [--out <zip の出力先>] [--json <情報ファイルの出力先>]')
      process.exit(0)
    } else {
      console.error(`不明な引数: ${arg}`)
      process.exit(2)
    }
  }
  if (options.json === null) {
    options.json = options.out.replace(/\.zip$/i, '.json')
  }
  return options
}

/** 拡張機能のフォルダを再帰的に集める（隠しファイルは入れない・名前順で安定させる）。 */
function collectFiles(root) {
  const files = []
  const walk = (dir) => {
    const entries = readdirSync(dir, { withFileTypes: true })
      .filter((entry) => !entry.name.startsWith('.'))
      // このスクリプト自身（配布物には要らない）
      .filter((entry) => entry.name !== 'build-zip.mjs')
      .sort((left, right) => left.name.localeCompare(right.name, 'en'))
    for (const entry of entries) {
      const full = join(dir, entry.name)
      if (entry.isDirectory()) {
        walk(full)
      } else if (entry.isFile()) {
        files.push(full)
      }
    }
  }
  walk(root)
  return files
}

const CRC_TABLE = (() => {
  const table = new Int32Array(256)
  for (let index = 0; index < 256; index += 1) {
    let value = index
    for (let bit = 0; bit < 8; bit += 1) {
      value = (value & 1) ? (0xedb88320 ^ (value >>> 1)) : (value >>> 1)
    }
    table[index] = value
  }
  return table
})()

function crc32(buffer) {
  let value = -1
  for (let index = 0; index < buffer.length; index += 1) {
    value = (value >>> 8) ^ CRC_TABLE[(value ^ buffer[index]) & 0xff]
  }
  return (value ^ -1) >>> 0
}

/** DOS 形式の日時（ZIP は 1980 年が最小）。 */
function dosDateTime(date) {
  const year = Math.max(1980, date.getFullYear())
  return {
    time: (date.getHours() << 11) | (date.getMinutes() << 5) | Math.floor(date.getSeconds() / 2),
    day: ((year - 1980) << 9) | ((date.getMonth() + 1) << 5) | date.getDate()
  }
}

/** ファイル一覧から zip（Buffer）を組み立てる。 */
function buildZip(entries) {
  const parts = []
  const central = []
  let offset = 0

  for (const entry of entries) {
    const nameBuffer = Buffer.from(entry.name, 'utf8')
    const raw = entry.data
    const deflated = deflateRawSync(raw, { level: 9 })
    // 小さなファイルは deflate のヘッダ分だけ大きくなることがある（その時は無圧縮で入れる）
    const useDeflate = deflated.length < raw.length
    const payload = useDeflate ? deflated : raw
    const method = useDeflate ? 8 : 0
    const crc = crc32(raw)
    const stamp = dosDateTime(entry.date)

    const local = Buffer.alloc(30)
    local.writeUInt32LE(0x04034b50, 0)
    local.writeUInt16LE(20, 4)
    local.writeUInt16LE(0x0800, 6) // ファイル名は UTF-8
    local.writeUInt16LE(method, 8)
    local.writeUInt16LE(stamp.time, 10)
    local.writeUInt16LE(stamp.day, 12)
    local.writeUInt32LE(crc, 14)
    local.writeUInt32LE(payload.length, 18)
    local.writeUInt32LE(raw.length, 22)
    local.writeUInt16LE(nameBuffer.length, 26)
    local.writeUInt16LE(0, 28)
    parts.push(local, nameBuffer, payload)

    const directory = Buffer.alloc(46)
    directory.writeUInt32LE(0x02014b50, 0)
    directory.writeUInt16LE(20, 4)
    directory.writeUInt16LE(20, 6)
    directory.writeUInt16LE(0x0800, 8)
    directory.writeUInt16LE(method, 10)
    directory.writeUInt16LE(stamp.time, 12)
    directory.writeUInt16LE(stamp.day, 14)
    directory.writeUInt32LE(crc, 16)
    directory.writeUInt32LE(payload.length, 20)
    directory.writeUInt32LE(raw.length, 24)
    directory.writeUInt16LE(nameBuffer.length, 28)
    directory.writeUInt16LE(0, 30)
    directory.writeUInt16LE(0, 32)
    directory.writeUInt16LE(0, 34)
    directory.writeUInt16LE(0, 36)
    directory.writeUInt32LE(0, 38)
    directory.writeUInt32LE(offset, 42)
    central.push(directory, nameBuffer)

    offset += local.length + nameBuffer.length + payload.length
  }

  const centralBuffer = Buffer.concat(central)
  const end = Buffer.alloc(22)
  end.writeUInt32LE(0x06054b50, 0)
  end.writeUInt16LE(0, 4)
  end.writeUInt16LE(0, 6)
  end.writeUInt16LE(entries.length, 8)
  end.writeUInt16LE(entries.length, 10)
  end.writeUInt32LE(centralBuffer.length, 12)
  end.writeUInt32LE(offset, 16)
  end.writeUInt16LE(0, 20)

  return Buffer.concat([...parts, centralBuffer, end])
}

function main() {
  const options = parseArgs(process.argv.slice(2))

  const manifestPath = join(EXTENSION_DIR, 'manifest.json')
  let manifest
  try {
    manifest = JSON.parse(readFileSync(manifestPath, 'utf8'))
  } catch (cause) {
    console.error(`manifest.json を読めません: ${manifestPath}`)
    console.error(String(cause))
    process.exit(1)
  }
  if (typeof manifest.version !== 'string' || manifest.version === '') {
    console.error('manifest.json に version がありません。')
    process.exit(1)
  }

  const files = collectFiles(EXTENSION_DIR)
  if (files.length === 0) {
    console.error(`拡張機能のファイルがありません: ${EXTENSION_DIR}`)
    process.exit(1)
  }

  const entries = files.map((file) => {
    const relativePath = relative(EXTENSION_DIR, file).split(sep).join('/')
    return {
      name: `${ROOT_IN_ZIP}/${relativePath}`,
      data: readFileSync(file),
      date: statSync(file).mtime
    }
  })
  // manifest.json は先頭に置く（中身を覗いたときに分かりやすい）
  entries.sort((left, right) => {
    const leftManifest = left.name.endsWith('/manifest.json')
    const rightManifest = right.name.endsWith('/manifest.json')
    if (leftManifest !== rightManifest) return leftManifest ? -1 : 1
    return left.name.localeCompare(right.name, 'en')
  })

  const zip = buildZip(entries)
  mkdirSync(dirname(options.out), { recursive: true })
  writeFileSync(options.out, zip)

  const info = {
    version: manifest.version,
    name: typeof manifest.name === 'string' ? manifest.name : '',
    file: options.out.split(sep).pop(),
    size: zip.length,
    files: entries.length,
    builtAt: new Date().toISOString(),
    url: '/downloads/' + options.out.split(sep).pop()
  }
  writeFileSync(options.json, JSON.stringify(info, null, 2) + '\n')

  console.log(`ブラウザ拡張の配布パッケージを作りました: ${options.out}`)
  console.log(`  version=${info.version} files=${info.files} size=${info.size} bytes`)
  console.log(`  情報ファイル: ${options.json}`)
}

main()
