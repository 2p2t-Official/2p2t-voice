#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"
DIST="$ROOT/dist"
mkdir -p "$DIST"

MOD_VERSION="$(grep '^mod_version=' gradle.properties | cut -d= -f2)"
if [[ -z "${VOICE_INTEGRITY_SECRET:-}" && -f "$ROOT/.integrity-secret" ]]; then
  VOICE_INTEGRITY_SECRET="$(tr -d '\n' < "$ROOT/.integrity-secret")"
  export VOICE_INTEGRITY_SECRET
fi
LOADER="${LOADER_VERSION:-0.19.3}"
LOOM="${LOOM_VERSION:-1.17-SNAPSHOT}"

TARGETS=(
  "26.1|0.145.1+26.1"
  "26.1.1|0.145.4+26.1.1"
  "26.1.2|0.155.2+26.1.2"
  "26.2|0.156.0+26.2"
)

restore() {
  git checkout -- src/main/java src/main/resources/fabric.mod.json gradle.properties build.gradle 2>/dev/null || true
}

build_one() {
  local mc="$1" fapi="$2"
  echo "=== Building $mc (api $fapi) ==="
  restore
  cp -f build.gradle.26 build.gradle

  cat > gradle.properties <<EOF
org.gradle.jvmargs=-Xmx3G
org.gradle.parallel=true

minecraft_version=${mc}
loader_version=${LOADER}
loom_version=${LOOM}
fabric_version=${fapi}
mod_version=${MOD_VERSION}
maven_group=org.twoptwot
archives_base_name=2p2tvoice
EOF

  python3 - <<PY
from pathlib import Path
import re
p = Path("src/main/resources/fabric.mod.json")
t = p.read_text()
t = re.sub(r'"minecraft":\s*"[^"]+"', '"minecraft": "~'"$mc"'"', t)
t = re.sub(r'"java":\s*"[^"]+"', '"java": ">=25"', t)
p.write_text(t)
PY

  python3 "$ROOT/scripts/port-to-26.py" "$mc"

  export JAVA_HOME="${JAVA_HOME_25:-/usr/lib/jvm/java-25-openjdk-amd64}"
  export PATH="$JAVA_HOME/bin:$PATH"

  if ./gradlew --no-daemon clean jar -q; then
    jar=$(ls -1 build/libs/2p2tvoice-*.jar 2>/dev/null | grep -v sources | grep -v -- '-dev' | head -1 || true)
    if [[ -n "${jar:-}" ]]; then
      out="$DIST/2p2tvoice-${MOD_VERSION}+${mc}.jar"
      cp -f "$jar" "$out"
      echo "OK $out"
      echo "$mc" >> "$DIST/built-26.txt"
    else
      echo "FAIL $mc (no jar)"
      echo "$mc" >> "$DIST/failed-26.txt"
    fi
  else
    echo "FAIL $mc (compile)"
    echo "$mc" >> "$DIST/failed-26.txt"
  fi
  restore
}

: > "$DIST/built-26.txt"
: > "$DIST/failed-26.txt"

for entry in "${TARGETS[@]}"; do
  IFS='|' read -r mc fapi <<<"$entry"
  build_one "$mc" "$fapi" || true
done

cat > gradle.properties <<EOF
org.gradle.jvmargs=-Xmx3G
org.gradle.parallel=true

minecraft_version=1.21.11
loader_version=0.19.3
loom_version=1.16.3
fabric_version=0.141.6+1.21.11
mod_version=${MOD_VERSION}
maven_group=org.twoptwot
archives_base_name=2p2tvoice
EOF
restore

echo
echo "Built 26.x:"
cat "$DIST/built-26.txt" || true
echo "Failed 26.x:"
cat "$DIST/failed-26.txt" || true
