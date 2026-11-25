#!/usr/bin/env bash
# Publishes a built version on GitHub (universocelular/video-downloader), from where the app updates itself.
# Before running it: raise versionMajor/Minor/Patch in app/build.gradle, build with assembleFossRelease and commit on
# the estadolisto branch. Usage, from the codigo folder:  bash scripts/publicar-version.sh notas.md
# notas.md holds what changed, in Spanish; it becomes the release text the app shows in "Registro de cambios".
set -euo pipefail

notes="${1:?usage: publicar-version.sh NOTES.md}"
gh="${GH:-/c/Users/unive/tools/gh/bin/gh.exe}"
export GH_TELEMETRY=0 GH_NO_UPDATE_NOTIFIER=1
repo=universocelular/video-downloader

version=$(grep -oE "versionName='[^']+'" <("${AAPT2:-/c/Users/unive/tools/android-sdk/build-tools/35.0.0/aapt2.exe}" dump badging \
  app/build/outputs/apk/foss/release/VideoDownloader-*-foss-arm64-v8a-release.apk) | head -1 | cut -d"'" -f2)
apks=(app/build/outputs/apk/foss/release/VideoDownloader-"$version"-foss-{arm64-v8a,armeabi-v7a}-release.apk)
for apk in "${apks[@]}"; do [ -f "$apk" ] || { echo "missing $apk: build first"; exit 1; }; done
[ -z "$(git status --porcelain)" ] || { echo "commit your changes on estadolisto first"; exit 1; }

# The public branch holds snapshots only: each one is estadolisto's files on top of the previous snapshot, so the
# YTDLnis history (a partial clone here) is never uploaded.
commit=$(git -c user.name=EstadoListo -c user.email=estadolisto@localhost commit-tree "estadolisto^{tree}" -p publico \
  -m "Video Downloader $version")
git branch -f publico "$commit"
git -c credential.helper= -c "credential.helper=!'$(cygpath -m "$gh")' auth git-credential" push origin publico:main

# the files go up as VideoDownloader-2.0.2-arm64-v8a.apk: versions up to 2.0.2 skip any name with "foss"
mkdir -p "../apk/v$version/publicar"
cp "${apks[@]}" "../apk/v$version/"
for abi in arm64-v8a armeabi-v7a; do
  cp "app/build/outputs/apk/foss/release/VideoDownloader-$version-foss-$abi-release.apk" "../apk/v$version/publicar/VideoDownloader-$version-$abi.apk"
done
(cd "../apk/v$version" && sha256sum *.apk publicar/*.apk > SHA256.txt)
"$gh" release create "v$version" "../apk/v$version/publicar/"*.apk --repo "$repo" --title "Video Downloader $version" --notes-file "$notes" --target main
echo "published v$version"
