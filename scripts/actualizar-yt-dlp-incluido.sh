#!/usr/bin/env bash
# Video Downloader: mete dentro de la app la última versión estable de yt-dlp.
# Baja el archivo "yt-dlp" de la última versión publicada en GitHub, comprueba su SHA-256
# contra la lista oficial (SHA2-256SUMS) y lo deja en app/src/main/res/raw/ytdlp.
# Hay que correrlo antes de cada compilación que se entregue.
set -euo pipefail
cd "$(dirname "$0")/.."

tmp=$(mktemp -d)
trap 'rm -rf "$tmp"' EXIT

curl -fsSL https://api.github.com/repos/yt-dlp/yt-dlp/releases/latest -o "$tmp/release.json"
version=$(node -e 'console.log(JSON.parse(require("fs").readFileSync(process.argv[1], "utf8")).tag_name)' "$tmp/release.json")
base="https://github.com/yt-dlp/yt-dlp/releases/download/$version"
curl -fsSL "$base/yt-dlp" -o "$tmp/yt-dlp"
curl -fsSL "$base/SHA2-256SUMS" -o "$tmp/SHA2-256SUMS"

expected=$(awk '$2 == "yt-dlp" {print $1}' "$tmp/SHA2-256SUMS")
actual=$(sha256sum "$tmp/yt-dlp" | cut -d' ' -f1)
if [ -z "$expected" ] || [ "$expected" != "$actual" ]; then
  echo "La suma SHA-256 de yt-dlp $version no coincide con la oficial; no se cambió nada." >&2
  exit 1
fi

mkdir -p app/src/main/res/raw
cp "$tmp/yt-dlp" app/src/main/res/raw/ytdlp
cat > app/src/main/res/values/ytdlp_incluido.xml <<EOF
<?xml version="1.0" encoding="utf-8"?>
<!-- Video Downloader: versión de yt-dlp que viaja dentro de la app (res/raw/ytdlp).
     La escribe scripts/actualizar-yt-dlp-incluido.sh; no editar a mano. -->
<resources>
    <string name="bundled_ytdlp_version" translatable="false">$version</string>
</resources>
EOF
echo "yt-dlp $version incluido en la app (SHA-256 $actual, igual al oficial)."
