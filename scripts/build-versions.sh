#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"
DIST="$ROOT/dist"
mkdir -p "$DIST"

MOD_VERSION="$(grep '^mod_version=' gradle.properties | cut -d= -f2 || true)"
if [[ -z "${MOD_VERSION}" ]]; then
  MOD_VERSION="$(grep '^mod.version' stonecutter.properties.toml 2>/dev/null | head -1 | cut -d\" -f2 || echo 1.1.0)"
fi
if [[ -z "${VOICE_INTEGRITY_SECRET:-}" && -f "$ROOT/.integrity-secret" ]]; then
  VOICE_INTEGRITY_SECRET="$(tr -d '\n' < "$ROOT/.integrity-secret")"
  export VOICE_INTEGRITY_SECRET
fi
LOADER="${LOADER_VERSION:-0.19.3}"
LOOM="${LOOM_VERSION:-1.16.3}"

TARGETS=(
  "1.21.11|0.141.6+1.21.11"
  "1.21.10|0.138.4+1.21.10"
  "1.21.9|0.134.1+1.21.9"
  "1.21.8|0.136.1+1.21.8"
  "1.21.7|0.129.0+1.21.7"
  "1.21.6|0.128.2+1.21.6"
  "1.21.5|0.128.2+1.21.5"
  "1.21.4|0.119.4+1.21.4"
  "1.21.3|0.114.1+1.21.3"
  "1.21.2|0.106.1+1.21.2"
  "1.21.1|0.116.15+1.21.1"
  "1.21|0.102.0+1.21"
)

restore_src() {
  git checkout -- src/main/java src/main/resources/fabric.mod.json gradle.properties 2>/dev/null || true
  python3 "$ROOT/scripts/restore-identifier-baseline.py" >/dev/null 2>&1 || true
}

build_one() {
  local mc="$1" fapi="$2"
  echo "=== Building $mc (api $fapi) ==="
  restore_src

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
t = re.sub(r'"minecraft":\s*"[^"]+"', '"minecraft": "~'"$mc"'"', p.read_text())
p.write_text(t)
PY

  python3 "$ROOT/scripts/preprocess-for-mc.py" "$mc"

  export JAVA_HOME="${JAVA_HOME_21:-/usr/lib/jvm/java-21-openjdk-amd64}"
  export PATH="$JAVA_HOME/bin:$PATH"

  if ./gradlew --no-daemon clean remapJar -q; then
    jar=$(ls -1 build/libs/2p2tvoice-*.jar 2>/dev/null | grep -v sources | grep -v -- '-dev' | head -1 || true)
    if [[ -n "${jar:-}" ]]; then
      out="$DIST/2p2tvoice-${MOD_VERSION}+${mc}.jar"
      cp -f "$jar" "$out"
      echo "OK $out"
      echo "$mc" >> "$DIST/built.txt"
    else
      echo "FAIL $mc (no jar)"
      echo "$mc" >> "$DIST/failed.txt"
    fi
  else
    echo "FAIL $mc (compile)"
    echo "$mc" >> "$DIST/failed.txt"
  fi
  restore_src
}

: > "$DIST/built.txt"
: > "$DIST/failed.txt"

for entry in "${TARGETS[@]}"; do
  IFS='|' read -r mc fapi <<<"$entry"
  build_one "$mc" "$fapi" || true
done

cat > gradle.properties <<EOF
org.gradle.jvmargs=-Xmx3G
org.gradle.parallel=true

minecraft_version=1.21.11
loader_version=${LOADER}
loom_version=${LOOM}
fabric_version=0.141.6+1.21.11
mod_version=${MOD_VERSION}
maven_group=org.twoptwot
archives_base_name=2p2tvoice
EOF
python3 - <<'PY'
from pathlib import Path
import re
p = Path("src/main/resources/fabric.mod.json")
t = re.sub(r'"minecraft":\s*"[^"]+"', '"minecraft": "~1.21.11"', p.read_text())
p.write_text(t)
PY
restore_src

echo
echo "Built:"
cat "$DIST/built.txt" || true
echo "Failed:"
cat "$DIST/failed.txt" || true
