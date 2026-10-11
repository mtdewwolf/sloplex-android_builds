#!/usr/bin/env bash
set -euo pipefail

for name in ANDROID_KEYSTORE_BASE64 ANDROID_KEYSTORE_PASSWORD ANDROID_KEY_ALIAS ANDROID_KEY_PASSWORD; do
  if [[ -z ${!name:-} ]]; then
    echo "Missing GitHub Actions secret: $name" >&2
    exit 1
  fi
done

key_file=$(mktemp "${RUNNER_TEMP:-${TMPDIR:-/tmp}}/sloplex-key-check.XXXXXX")
csr_file=$(mktemp "${RUNNER_TEMP:-${TMPDIR:-/tmp}}/sloplex-csr-check.XXXXXX")
trap 'rm -f "$key_file" "$csr_file"' EXIT
if ! printf '%s' "$ANDROID_KEYSTORE_BASE64" | base64 --decode > "$key_file" 2>/dev/null; then
  echo "ANDROID_KEYSTORE_BASE64 is not valid Base64. Encode the entire keystore file and upload it again." >&2
  exit 1
fi

# A local, discarded CSR proves that both the keystore and private key can be unlocked.
# Suppress keytool details so signing secrets and certificate metadata stay out of failure logs.
if ! keytool -certreq -keystore "$key_file" -alias "$ANDROID_KEY_ALIAS" \
  -storepass:env ANDROID_KEYSTORE_PASSWORD -keypass:env ANDROID_KEY_PASSWORD \
  -file "$csr_file" >/dev/null 2>&1; then
  echo "Cannot open the Android signing key. Check that ANDROID_KEYSTORE_BASE64 contains the complete keystore file, and verify ANDROID_KEYSTORE_PASSWORD, ANDROID_KEY_ALIAS and ANDROID_KEY_PASSWORD." >&2
  exit 1
fi
echo "Android signing keystore, alias and passwords verified."
