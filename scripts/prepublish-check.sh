#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

fail() {
  echo "PREPUBLISH FAIL: $*" >&2
  exit 1
}

echo "=== prepublish check ==="

if [[ -n "$(git status --porcelain)" ]]; then
  fail "working tree is dirty"
fi

MOD_VERSION="$(grep '^mod_version=' gradle.properties | cut -d= -f2)"
[[ -n "$MOD_VERSION" ]] || fail "mod_version missing"
TAG="v${MOD_VERSION}"

if [[ -z "${VOICE_INTEGRITY_SECRET:-}" && -f "$ROOT/.integrity-secret" ]]; then
  VOICE_INTEGRITY_SECRET="$(tr -d '\n' < "$ROOT/.integrity-secret")"
  export VOICE_INTEGRITY_SECRET
fi
[[ -n "${VOICE_INTEGRITY_SECRET:-}" ]] || fail "VOICE_INTEGRITY_SECRET missing"

comment_hits="$(find src -name '*.java' -print0 | xargs -0 grep -nE '(^|[[:space:]])//|/\*|\*/' || true)"
if [[ -n "$comment_hits" ]]; then
  echo "$comment_hits" >&2
  fail "Java comments found under src/"
fi

for req in \
  src/main/resources/assets/twoptwotvoice/textures/gui/logo.png \
  src/main/resources/assets/twoptwotvoice/textures/gui/menu_bg.png \
  src/main/java/org/twoptwot/voice/ui/menu/TwopTitleScreen.java \
  src/main/java/org/twoptwot/voice/ui/menu/TwopMultiplayerScreen.java \
  src/main/java/org/twoptwot/voice/ui/menu/TwopJoinMultiplayerScreen.java
do
  [[ -f "$req" ]] || fail "missing $req"
done

grep -q 'MenuScreens.register' src/main/java/org/twoptwot/voice/TwoptwotVoiceClient.java \
  || fail "MenuScreens not registered"

export JAVA_HOME="${JAVA_HOME:-/usr/lib/jvm/java-21-openjdk-amd64}"
export PATH="$JAVA_HOME/bin:$PATH"

echo "Smoke compile current matrix target..."
./gradlew compileJava --no-daemon -q

echo "Smoke build 1.21.1..."
./scripts/build-one.sh 1.21.1 >/tmp/voice-prepublish-1.21.1.log 2>&1 \
  || { tail -40 /tmp/voice-prepublish-1.21.1.log >&2; fail "build-one 1.21.1 failed"; }

echo "Smoke build 26.1..."
./scripts/build-one.sh 26.1 >/tmp/voice-prepublish-26.1.log 2>&1 \
  || { tail -40 /tmp/voice-prepublish-26.1.log >&2; fail "build-one 26.1 failed"; }

[[ -f "dist/twoptwotvoice-${MOD_VERSION}+1.21.1.jar" ]] || fail "missing 1.21.1 jar"
[[ -f "dist/twoptwotvoice-${MOD_VERSION}+26.1.jar" ]] || fail "missing 26.1 jar"

if gh release view "$TAG" --repo "${GITHUB_REPO:-2p2t-Official/2p2t-voice}" >/dev/null 2>&1; then
  echo "NOTE: release $TAG already exists and will be updated on publish."
fi

echo "PREPUBLISH OK for ${MOD_VERSION}"
