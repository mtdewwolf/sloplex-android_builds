#!/usr/bin/env bash
set -euo pipefail

if [[ $# -ne 2 ]]; then
  echo "Usage: $0 unsigned.apk signed.apk" >&2
  exit 1
fi
for name in ANDROID_KEYSTORE_BASE64 ANDROID_KEYSTORE_PASSWORD ANDROID_KEY_ALIAS ANDROID_KEY_PASSWORD ANDROID_HOME; do
  if [[ -z ${!name:-} ]]; then
    echo "Missing required environment variable: $name" >&2
    exit 1
  fi
done

build_tools="$ANDROID_HOME/build-tools/35.0.0"
keystore=$(mktemp "${RUNNER_TEMP:-${TMPDIR:-/tmp}}/sloplex-signing.XXXXXX")
trap 'rm -f "$keystore"' EXIT
chmod 600 "$keystore"
printf '%s' "$ANDROID_KEYSTORE_BASE64" | tr -d '[:space:]' | base64 --decode > "$keystore"
mkdir -p "$(dirname "$2")"
"$build_tools/zipalign" -c -P 16 4 "$1"
"$build_tools/apksigner" sign \
  --ks "$keystore" --ks-key-alias "$ANDROID_KEY_ALIAS" \
  --ks-pass env:ANDROID_KEYSTORE_PASSWORD --key-pass env:ANDROID_KEY_PASSWORD \
  --v4-signing-enabled false --out "$2" "$1"
"$build_tools/apksigner" verify --verbose --print-certs "$2"
(
  cd "$(dirname "$2")"
  sha256sum "$(basename "$2")" > "$(basename "$2").sha256"
  sha256sum --check "$(basename "$2").sha256"
)
