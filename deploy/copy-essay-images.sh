#!/usr/bin/env bash
# ============================================================================
# 2.0 -> 2.1  英作文の画像の実体をコピーする（**旧サーバで実行**する）
# ----------------------------------------------------------------------------
# 2.0: <tomcat>/webapps/file/english-essay/<ユーザーID>/<uuid>/<ファイル>
# 2.1: <storage-root>/english-essay/legacy/<ユーザーID>/<uuid>/<ファイル>
#      （DB 側の 相対パス には移行時に 'english-essay/legacy/' を前置してある）
#
# 使い方（例）:
#   ./copy-essay-images.sh \
#       --from /vol5/1000/DATA0/tomcat/webapps/file/english-essay \
#       --to   /vol5/1000/DATA0/study21/data/english-essay/legacy
#
# 何度実行しても同じ（既にあるファイルは上書きしない）。--dry-run で確認だけできる。
# ============================================================================
set -euo pipefail

FROM=""
TO=""
DRY_RUN=0

while [ $# -gt 0 ]; do
  case "$1" in
    --from) FROM="${2:-}"; shift 2 ;;
    --to)   TO="${2:-}";   shift 2 ;;
    --dry-run) DRY_RUN=1; shift ;;
    -h|--help) sed -n '2,16p' "$0"; exit 0 ;;
    *) echo "知らない引数: $1" >&2; exit 2 ;;
  esac
done

if [ -z "$FROM" ] || [ -z "$TO" ]; then
  echo "使い方: $0 --from <2.0 の english-essay ディレクトリ> --to <2.1 の legacy ディレクトリ> [--dry-run]" >&2
  exit 2
fi
if [ ! -d "$FROM" ]; then
  echo "コピー元がありません: $FROM" >&2
  exit 1
fi

echo "コピー元: $FROM"
echo "コピー先: $TO"
[ "$DRY_RUN" -eq 1 ] && echo "（--dry-run: 実際にはコピーしません）"

# 対象（jpg/png/webp。2.0 は jpg が主）
mapfile -t FILES < <(find "$FROM" -type f \( -iname '*.jpg' -o -iname '*.jpeg' -o -iname '*.png' -o -iname '*.webp' \) | sort)

if [ "${#FILES[@]}" -eq 0 ]; then
  echo "画像が見つかりませんでした（コピー元を確認してください）"
  exit 0
fi

copied=0
skipped=0
for file in "${FILES[@]}"; do
  rel="${file#"$FROM"/}"
  dest="$TO/$rel"
  if [ -f "$dest" ]; then
    skipped=$((skipped + 1))
    continue
  fi
  if [ "$DRY_RUN" -eq 1 ]; then
    echo "  [dry-run] $rel"
  else
    mkdir -p "$(dirname "$dest")"
    cp -p "$file" "$dest"
  fi
  copied=$((copied + 1))
done

echo "コピー: $copied 件 / 既にあったので飛ばした: $skipped 件 / 対象: ${#FILES[@]} 件"
echo "確認: SELECT count(*) FROM public.\"ENG_英作文画像情報\"; （DB は 83 件のはず）"
