#!/usr/bin/env bash
# Puts this repo's own website and APK links into web/config.js (only where they're still empty).
set -euo pipefail
owner=$(echo "$GITHUB_REPOSITORY_OWNER" | tr '[:upper:]' '[:lower:]')
repo=${GITHUB_REPOSITORY#*/}
if [ "$repo" = "$owner.github.io" ]; then site="https://$owner.github.io/"; else site="https://$owner.github.io/$repo/"; fi
apk="https://github.com/$GITHUB_REPOSITORY/releases/latest/download/whats-in-your-bin.apk"
sed -i "s#publicUrl: \"\"#publicUrl: \"$site\"#; s#apkUrl: \"\"#apkUrl: \"$apk\"#" web/config.js
grep -n "publicUrl\|apkUrl" web/config.js
