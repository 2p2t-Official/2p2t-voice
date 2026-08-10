#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

if [[ $# -lt 1 ]]; then
  cat <<'EOF'
Build one Minecraft version of 2p2t Voice.

Usage:
  ./scripts/build-one.sh <minecraft_version>

Examples:
  ./scripts/build-one.sh 1.21.11
  ./scripts/build-one.sh 1.21.4
  ./scripts/build-one.sh 26.2

The jar lands in dist/ as 2p2tvoice-<mod>+<minecraft>.jar
EOF
  exit 1
fi

MC="$1"
MOD_VERSION="$(grep '^mod_version=' gradle.properties | cut -d= -f2)"
DIST="$ROOT/dist"
mkdir -p "$DIST"

if [[ -z "${VOICE_INTEGRITY_SECRET:-}" && -f "$ROOT/.integrity-secret" ]]; then
  VOICE_INTEGRITY_SECRET="$(tr -d '\n' < "$ROOT/.integrity-secret")"
  export VOICE_INTEGRITY_SECRET
fi

declare -A FAPI=(
  ["1.21.11"]="0.141.6+1.21.11"
  ["1.21.10"]="0.138.4+1.21.10"
  ["1.21.9"]="0.134.1+1.21.9"
  ["1.21.8"]="0.136.1+1.21.8"
  ["1.21.7"]="0.129.0+1.21.7"
  ["1.21.6"]="0.128.2+1.21.6"
  ["1.21.5"]="0.128.2+1.21.5"
  ["1.21.4"]="0.119.4+1.21.4"
  ["1.21.3"]="0.114.1+1.21.3"
  ["1.21.2"]="0.106.1+1.21.2"
  ["1.21.1"]="0.116.15+1.21.1"
  ["1.21"]="0.102.0+1.21"
  ["26.1"]="0.145.1+26.1"
  ["26.1.1"]="0.145.4+26.1.1"
  ["26.1.2"]="0.155.2+26.1.2"
  ["26.2"]="0.156.0+26.2"
)

if [[ -z "${FAPI[$MC]+x}" ]]; then
  echo "Unknown Minecraft version: $MC" >&2
  echo "Supported: ${!FAPI[*]}" >&2
  exit 1
fi

API="${FAPI[$MC]}"
IS_26=0
if [[ "$MC" == 26.* ]]; then
  IS_26=1
fi

restore() {
  git checkout -- src/main/java src/main/resources/fabric.mod.json gradle.properties build.gradle 2>/dev/null || true
}

cleanup() {
  restore
}
trap cleanup EXIT

restore

if [[ "$IS_26" -eq 1 ]]; then
  cp -f build.gradle.26 build.gradle
  LOOM="${LOOM_VERSION:-1.17-SNAPSHOT}"
  LOADER="${LOADER_VERSION:-0.19.3}"
  JAVA_HOME_USE="${JAVA_HOME_25:-/usr/lib/jvm/java-25-openjdk-amd64}"
else
  LOOM="${LOOM_VERSION:-1.16.3}"
  LOADER="${LOADER_VERSION:-0.19.3}"
  JAVA_HOME_USE="${JAVA_HOME_21:-/usr/lib/jvm/java-21-openjdk-amd64}"
fi

cat > gradle.properties <<EOF
org.gradle.jvmargs=-Xmx3G
org.gradle.parallel=true

minecraft_version=${MC}
loader_version=${LOADER}
loom_version=${LOOM}
fabric_version=${API}
mod_version=${MOD_VERSION}
maven_group=org.twoptwot
archives_base_name=2p2tvoice
EOF

python3 - <<PY
from pathlib import Path
import re
p = Path("src/main/resources/fabric.mod.json")
t = p.read_text()
t = re.sub(r'"minecraft":\s*"[^"]+"', '"minecraft": "~'"$MC"'"', t)
if "$IS_26" == "1":
    t = re.sub(r'"java":\s*"[^"]+"', '"java": ">=25"', t)
p.write_text(t)
PY

if [[ "$IS_26" -eq 1 ]]; then
  python3 "$ROOT/scripts/port-to-26.py" "$MC"
else
  python3 "$ROOT/scripts/preprocess-for-mc.py" "$MC"
fi

export JAVA_HOME="$JAVA_HOME_USE"
export PATH="$JAVA_HOME/bin:$PATH"

if [[ "$IS_26" -eq 1 ]]; then
  ./gradlew --no-daemon clean jar
else
  ./gradlew --no-daemon clean remapJar
fi

jar=$(ls -1 build/libs/2p2tvoice-*.jar 2>/dev/null | grep -v sources | grep -v -- '-dev' | head -1 || true)
if [[ -z "${jar:-}" ]]; then
  echo "Build finished but no jar was found in build/libs/" >&2
  exit 1
fi

out="$DIST/2p2tvoice-${MOD_VERSION}+${MC}.jar"
cp -f "$jar" "$out"
echo
echo "Built: $out"
echo "Copy that into your .minecraft/mods folder (with Fabric API for $MC)."
