#!/usr/bin/env bash
# Puts this repo's website and APK links into web/config.js, plus the sync and Google settings saved as
# repository variables (Settings → Secrets and variables → Actions → Variables), so phones need no setup.
# Only fills values that are still empty in config.js.
set -euo pipefail
owner=$(echo "$GITHUB_REPOSITORY_OWNER" | tr '[:upper:]' '[:lower:]')
repo=${GITHUB_REPOSITORY#*/}
if [ "$repo" = "$owner.github.io" ]; then site="https://$owner.github.io/"; else site="https://$owner.github.io/$repo/"; fi
apk="https://github.com/$GITHUB_REPOSITORY/releases/latest/download/whats-in-your-bin.apk"
fill() { # fill <key> <value>
  [ -n "$2" ] || return 0
  local v=${2//\\/\\\\}; v=${v//#/\\#}; v=${v//&/\\&}; v=${v//\"/}
  sed -i "s#$1: \"\"#$1: \"$v\"#" web/config.js
}
fill publicUrl "$site"
fill apkUrl "$apk"
fill supabaseUrl "${SUPABASE_URL:-}"
fill supabaseAnonKey "${SUPABASE_ANON_KEY:-}"
fill googleClientId "${GOOGLE_CLIENT_ID:-}"
grep -n "publicUrl\|apkUrl\|supabaseUrl" web/config.js
