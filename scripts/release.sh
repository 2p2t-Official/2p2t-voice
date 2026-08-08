#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

REPO="${GITHUB_REPO:-WaffleStealz/2p2t-voice}"
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

if [[ -n "$(git status --porcelain)" ]]; then
  echo "Working tree is dirty. Commit (or stash) before releasing so matrix restores are clean." >&2
  git status --short >&2
  exit 1
fi

MOD_VERSION="$(grep '^mod_version=' gradle.properties | cut -d= -f2)"
if [[ -z "${MOD_VERSION}" ]]; then
  echo "mod_version missing from gradle.properties" >&2
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
fi

missing=()
jars=()
for mc in "${EXPECTED[@]}"; do
  jar="$DIST/twoptwotvoice-${MOD_VERSION}+${mc}.jar"
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

echo "All ${#jars[@]} release jars present for ${MOD_VERSION}."

if [[ "$SKIP_UPLOAD" -eq 1 ]]; then
  echo "Skipping upload (--skip-upload)."
  printf '%s\n' "${jars[@]}"
  exit 0
fi

if [[ -z "$NOTES" ]]; then
  NOTES="2p2t Voice ${MOD_VERSION}

Full Minecraft matrix (same set as v1.1.0):
$(printf -- '- %s\n' "${EXPECTED[@]}")

Install the jar matching your game version."
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
gh release view "$TAG" --repo "$REPO" --json assets --jq '.assets[].name' | sort
