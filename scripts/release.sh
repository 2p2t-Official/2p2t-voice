#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

REPO="${GITHUB_REPO:-2p2t-Official/2p2t-voice}"
SKIP_BUILD=0
SKIP_UPLOAD=0
NOTES=""

usage() {
  sed -n '2,20p' "$0"
  exit 0
}

for arg in "$@"; do
  case "$arg" in
    --skip-build) SKIP_BUILD=1 ;;
    --skip-upload) SKIP_UPLOAD=1 ;;
    --help|-h) usage ;;
    --notes=*) NOTES="${arg#--notes=}" ;;
    *)
      echo "Unknown argument: $arg" >&2
      exit 1
      ;;
  esac
done

if [[ "${PREPUBLISH_SKIP:-0}" != "1" ]]; then
  ./scripts/prepublish-check.sh
fi

MOD_VERSION="$(grep '^mod_version=' gradle.properties | cut -d= -f2)"
if [[ -z "${MOD_VERSION}" ]]; then
  echo "mod_version missing from gradle.properties" >&2
  exit 1
fi
if [[ -z "${VOICE_INTEGRITY_SECRET:-}" && -f "$ROOT/.integrity-secret" ]]; then
  VOICE_INTEGRITY_SECRET="$(tr -d '\n' < "$ROOT/.integrity-secret")"
  export VOICE_INTEGRITY_SECRET
fi
if [[ -z "${VOICE_INTEGRITY_SECRET:-}" ]]; then
  echo "VOICE_INTEGRITY_SECRET missing" >&2
  exit 1
fi
TAG="v${MOD_VERSION}"

EXPECTED=(
  1.21
  1.21.1
  1.21.2
  1.21.3
  1.21.4
  1.21.5
  1.21.6
  1.21.7
  1.21.8
  1.21.9
  1.21.10
  1.21.11
  26.1
  26.1.1
  26.1.2
  26.2
)

DIST="$ROOT/dist"
mkdir -p "$DIST"

if [[ "$SKIP_BUILD" -eq 0 ]]; then
  echo "=== Building 1.21.x matrix ==="
  ./scripts/build-versions.sh
  echo "=== Building 26.x matrix ==="
  ./scripts/build-26.sh
  echo "=== Building loader ==="
  ./scripts/build-loader.sh
fi

LOADER_JAR="$DIST/2p2tvoice-0-loader-${MOD_VERSION}.jar"
if [[ ! -f "$LOADER_JAR" ]]; then
  echo "Release aborted: missing loader jar $LOADER_JAR" >&2
  exit 1
fi

missing=()
jars=("$LOADER_JAR")
for mc in "${EXPECTED[@]}"; do
  jar="$DIST/2p2tvoice-${MOD_VERSION}+${mc}.jar"
  if [[ -f "$jar" ]]; then
    jars+=("$jar")
  else
    missing+=("$mc")
  fi
done

if ((${#missing[@]} > 0)); then
  echo "Release aborted: missing jars for: ${missing[*]}" >&2
  echo "Failed 1.21.x:" >&2
  cat "$DIST/failed.txt" 2>/dev/null || true
  echo "Failed 26.x:" >&2
  cat "$DIST/failed-26.txt" 2>/dev/null || true
  exit 1
fi

echo "All $((${#jars[@]})) release jars present for ${MOD_VERSION} (loader first)."

HASH_FILE="$DIST/voice-allowed-hashes-${MOD_VERSION}.txt"
: > "$HASH_FILE"
echo "2p2tvoice ${MOD_VERSION}" >> "$HASH_FILE"
for jar in "${jars[@]}"; do
  base="$(basename "$jar")"
  if [[ "$base" == *loader* ]]; then
    continue
  fi
  python3 "$ROOT/scripts/jar-content-hash.py" "$jar" | awk '{print $1}' >> "$HASH_FILE"
done
echo "Wrote $HASH_FILE"

SERVER_HASHES="${VOICE_ALLOWED_HASHES_FILE:-/var/lib/pterodactyl/volumes/4f8f956f-544b-4411-baad-7b12821c4e96/plugins/2p2tCore/voice-allowed-hashes.txt}"
if [[ -n "$SERVER_HASHES" ]]; then
  mkdir -p "$(dirname "$SERVER_HASHES")"
  touch "$SERVER_HASHES"
  while read -r hash; do
    [[ "$hash" =~ ^[0-9a-f]{64}$ ]] || continue
    if ! grep -qxF "$hash" "$SERVER_HASHES"; then
      echo "$hash" >> "$SERVER_HASHES"
    fi
  done < <(grep -E '^[0-9a-f]{64}$' "$HASH_FILE")
  echo "Updated server allowlist: $SERVER_HASHES"
fi

if [[ "$SKIP_UPLOAD" -eq 1 ]]; then
  echo "Skipping upload (--skip-upload)."
  printf '%s\n' "${jars[@]}"
  exit 0
fi

if [[ -z "$NOTES" ]]; then
  NOTES="${MOD_VERSION}"
fi

if gh release view "$TAG" --repo "$REPO" >/dev/null 2>&1; then
  echo "Updating existing release $TAG on $REPO ..."
  gh release upload "$TAG" "${jars[@]}" --clobber --repo "$REPO"
  gh release edit "$TAG" --repo "$REPO" --notes "$NOTES" >/dev/null
else
  echo "Creating release $TAG on $REPO ..."
  gh release create "$TAG" "${jars[@]}" \
    --repo "$REPO" \
    --title "$MOD_VERSION" \
    --notes "$NOTES"
fi

echo "Published $TAG with ${#jars[@]} assets:"
printf '%s\n' "${jars[@]}" | xargs -n1 basename
