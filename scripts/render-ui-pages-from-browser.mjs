// Study 2.1 — 用真实浏览器渲染 ui-demo 页面，抓取“已填充”的 DOM 重新生成 Vue 页面。
// 原 scripts/generate-ui-pages.mjs 使用 JSDOM（不执行页面脚本），因此表格/列表等动态数据是空的。
// 本脚本改用 puppeteer-core + 系统 Chrome：mock-state.js 与页面脚本会填充假数据，抓取后写入生成页。
// 用法: node scripts/render-ui-pages-from-browser.mjs [ui-demo源目录]
import { createRequire } from 'node:module'
import { mkdir, readdir, readFile, rm, writeFile } from 'node:fs/promises'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const scriptDir = path.dirname(fileURLToPath(import.meta.url))
const projectRoot = path.resolve(scriptDir, '..')
const sourceDir = path.resolve(process.argv[2] ?? 'C:/Users/Administrator/Desktop/ui-demo')
const outputDir = path.join(projectRoot, 'frontend', 'pc-web', 'src', 'views', 'prototype', 'generated')
const registryFile = path.join(projectRoot, 'frontend', 'pc-web', 'src', 'config', 'prototypePages.generated.ts')

const require = createRequire(path.join(projectRoot, 'frontend', 'package.json'))
const puppeteer = require('puppeteer-core')

const CHROME = process.env.STUDY21_CHROME ?? 'C:/Program Files/Google/Chrome/Application/chrome.exe'

function slugFor(filename) {
  return path.basename(filename, '.html')
    .replace(/副本/g, 'copy')
    .replace(/[^a-zA-Z0-9]+/g, '-')
    .replace(/^-+|-+$/g, '')
    .toLowerCase()
}

function componentNameFor(slug) {
  return `${slug.split('-').map((part) => part.charAt(0).toUpperCase() + part.slice(1)).join('')}Page`
}

function textLiteral(value) {
  return JSON.stringify(value.replace(/\s+/g, ' ').trim())
}

function extractMountText(html, key) {
  const match = html.match(new RegExp(`${key}\\s*:\\s*["']([^"']*)["']`))
  return match?.[1] ?? ''
}

// 検索ボタンは 2.1 の基準（臨時ファイル管理の検索ボタン = btn btn--primary + 検索アイコン）に揃える。
// ui-demo 側でアイコンの有無・クラスが揺れているため、生成時にここで正規化する。
const SEARCH_ICON = '<svg class="icon"><use href="/prototype-assets/icons/icons.svg#i-search"></use></svg>'
function normalizeSearchButtons(html) {
  return html.replace(/<button\b([^>]*)>([^<]*検索[^<]*)<\/button>/g, (whole, attrs, label) => {
    const classMatch = attrs.match(/class="([^"]*)"/)
    const classes = classMatch ? classMatch[1] : ''
    // ボタン以外（タブなど）は対象外
    if (!/\bbtn\b/.test(classes) || /pill-tabs/.test(classes)) return whole
    const hasIcon = /<svg|i-search/.test(attrs)
    let nextAttrs = attrs
    if (!/btn--(primary|secondary|danger|ghost)/.test(classes)) {
      nextAttrs = classMatch
        ? attrs.replace(`class="${classes}"`, `class="${`${classes} btn--primary`.trim()}"`)
        : ` class="btn btn--primary"${attrs}`
    }
    return `<button${nextAttrs}>${hasIcon ? label : SEARCH_ICON + label.replace(/^\s+/, '')}</button>`
  })
}

// 削除（ゴミ箱）アイコンボタンはアプリ共通の赤（.btn--icon.is-danger）に揃える。
function normalizeDangerButtons(html) {
  return html.replace(/<button\b([^>]*title="削除"[^>]*)>/g, (whole, attrs) => {
    const classMatch = attrs.match(/class="([^"]*)"/)
    const classes = classMatch ? classMatch[1] : ''
    if (!/\bbtn--icon\b/.test(classes) || /\bis-danger\b/.test(classes)) return whole
    return `<button${attrs.replace(`class="${classes}"`, `class="${classes} is-danger"`)}>`
  })
}

// 操作アイコンの色は 2.1 の共通ルール（削除=赤 / 編集=青 / 参照（目）=緑）に揃える。
// ボタンではなくアイコン自身にクラスを付けるので、btn--icon 以外のボタンや見出しでも同じ色になる。
// ダウンロードなどその他のアイコンは ui-demo のまま（変更しない）。
const ICON_COLOR_CLASSES = { 'i-trash': 'icon--danger', 'i-edit': 'icon--edit', 'i-eye': 'icon--view' }
const ICON_COLOR_PATTERN =
  /<svg class="icon"([^>]*)><use href="\/prototype-assets\/icons\/icons\.svg#(i-trash|i-edit|i-eye)"><\/use><\/svg>/g
function normalizeIconColors(html) {
  return html.replace(ICON_COLOR_PATTERN, (whole, attrs, iconId) => {
    const colorClass = ICON_COLOR_CLASSES[iconId]
    if (!colorClass) return whole
    return `<svg class="icon ${colorClass}"${attrs}><use href="/prototype-assets/icons/icons.svg#${iconId}"></use></svg>`
  })
}

// 在页面上下文执行：将已填充的 DOM 抽取为干净片段（与旧 JSDOM 脚本同一套转换）。
function prepareFragmentInPage() {
  const doc = document
  const slug = (value) =>
    String(value)
      .replace(/\.html(?:[?#].*)?$/i, '')
      .replace(/副本/g, 'copy')
      .replace(/[^a-zA-Z0-9]+/g, '-')
      .replace(/^-+|-+$/g, '')
      .toLowerCase()

  const pageBody = doc.querySelector('.page-body')
  const host = doc.createElement('div')

  if (pageBody) {
    for (const child of [...pageBody.childNodes]) host.appendChild(child.cloneNode(true))
    for (const dialog of doc.querySelectorAll('.overlay')) {
      if (!pageBody.contains(dialog)) host.appendChild(dialog.cloneNode(true))
    }
  } else {
    for (const child of [...doc.body.childNodes]) host.appendChild(child.cloneNode(true))
    for (const removable of host.querySelectorAll('script, aside.sidebar, .sidebar-backdrop, header.topbar, #pageHead')) {
      removable.remove()
    }
  }

  for (const element of host.querySelectorAll('*')) {
    for (const attribute of [...element.attributes]) {
      if (attribute.name.toLowerCase().startsWith('on')) {
        const routeMatch = attribute.value.match(/["']([^"']+\.html(?:[?#][^"']*)?)["']/i)
        if (routeMatch) element.setAttribute('data-route-screen', slug(routeMatch[1]))
        element.removeAttribute(attribute.name)
      }
    }

    for (const attributeName of ['href', 'data-nav', 'data-href']) {
      const value = element.getAttribute(attributeName)
      const match = value?.match(/([^/]+)\.html(?:[?#].*)?$/i)
      if (match) {
        element.setAttribute('data-route-screen', slug(match[1] + '.html'))
        if (attributeName === 'href') element.setAttribute('href', '#')
        else element.removeAttribute(attributeName)
      }
    }

    if (element.tagName === 'IFRAME' && /\.html(?:[?#].*)?$/i.test(element.getAttribute('src') ?? '')) {
      const src = element.getAttribute('src') ?? ''
      element.setAttribute('data-route-screen', slug(String(src).split('/').pop()))
      element.removeAttribute('src')
    }

    if (element.tagName.toLowerCase() === 'use') {
      const href = element.getAttribute('href')
      if (href?.startsWith('#')) element.setAttribute('href', `/prototype-assets/icons/icons.svg${href}`)
    }

    if (element.tagName === 'FORM') element.removeAttribute('action')
    element.removeAttribute('target')
  }

  return host.innerHTML
    .replace(/\s(disabled|readonly|multiple|required|checked|selected|autofocus|controls|open|hidden)=""/gi, ' $1')
    .replace(/\u3000/g, ' ')
    .replace(/{{/g, '&#123;&#123;')
    .replace(/}}/g, '&#125;&#125;')
    .trim()
}

const browser = await puppeteer.launch({
  executablePath: CHROME,
  headless: true,
  args: ['--no-sandbox', '--disable-gpu', '--allow-file-access-from-files', '--disable-dev-shm-usage']
})

try {
  await rm(outputDir, { recursive: true, force: true })
  await mkdir(outputDir, { recursive: true })

  const filenames = (await readdir(sourceDir))
    .filter((name) => name.toLowerCase().endsWith('.html') && name.toLowerCase() !== 'login.html')
    .sort((a, b) => a.localeCompare(b, 'en'))

  const pages = []
  const slugs = new Set()

  const page = await browser.newPage()

  for (const filename of filenames) {
    const filePath = path.join(sourceDir, filename)
    const html = await readFile(filePath, 'utf8')

    // Pure redirect stubs (small files containing a meta refresh / location.replace):
    // do not navigate (Chrome would follow the redirect and yield the target's content).
    const redirectStub = /(http-equiv="refresh"|window\.location\.replace)/i.test(html) && html.length < 2000

    const slug = slugFor(filename)
    if (slugs.has(slug)) throw new Error(`Duplicate generated slug: ${slug}`)
    slugs.add(slug)

    const componentName = componentNameFor(slug)
    let fragment
    if (redirectStub) {
      fragment = ''
    } else {
      try {
        const url = 'file:///' + filePath.replace(/\\/g, '/')
        await page.goto(url, { waitUntil: 'networkidle0', timeout: 30000 })
        await new Promise((resolve) => setTimeout(resolve, 1500))
        fragment = await page.evaluate(prepareFragmentInPage)
        fragment = normalizeSearchButtons(fragment)
        fragment = normalizeDangerButtons(fragment)
        fragment = normalizeIconColors(fragment)
      } catch (error) {
        console.warn(`[render] ${filename}: ${error.message}`)
        continue
      }
    }

    const pageTitle = extractMountText(html, 'title') || html.match(/<title>([^<]*)<\/title>/i)?.[1]?.replace(/^STUDY 2\.0\s*[-–]\s*/i, '') || slug
    const subtitle = extractMountText(html, 'sub')
    const styles = [...new Set([...html.matchAll(/<style[^>]*>([\s\S]*?)<\/style>/gi)].map((m) => m[1]))].join('\n')

    const component = `<template>\n  <div class="prototype-screen prototype-screen--${slug}">\n${fragment.split('\n').map((line) => `    ${line}`).join('\n')}\n  </div>\n</template>\n\n<style scoped>\n${styles}\n</style>\n`
    await writeFile(path.join(outputDir, `${componentName}.vue`), component, 'utf8')
    pages.push({ slug, filename, title: pageTitle, subtitle, component: componentName })
    console.log(`[${pages.length}] ${filename} -> ${componentName} (${fragment.length} chars)`)
  }

  await page.close()

  const registry =
    `// Generated by scripts/render-ui-pages-from-browser.mjs. Do not edit manually.\n` +
    `export interface PrototypePageDefinition {\n  slug: string\n  filename: string\n  title: string\n  subtitle: string\n  component: string\n}\n\n` +
    `export const prototypePages: PrototypePageDefinition[] = ${JSON.stringify(pages, null, 2)}\n\n` +
    `export const prototypePageMap = new Map(prototypePages.map((page) => [page.slug, page]))\n`

  await writeFile(registryFile, registry, 'utf8')
  console.log(`Generated ${pages.length} Vue pages in ${outputDir}`)
} finally {
  await browser.close()
}
