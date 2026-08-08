#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"
DIST="$ROOT/dist"
mkdir -p "$DIST"

MOD_VERSION="$(grep '^mod_version=' gradle.properties | cut -d= -f2)"
LOADER="$(grep '^loader_version=' gradle.properties | cut -d= -f2)"
LOOM="$(grep '^loom_version=' gradle.properties | cut -d= -f2)"

# game|fabric_api|loom_plugin(legacy|modern)|java
TARGETS=(
  "1.21.11|0.141.6+1.21.11|legacy|21"
  "1.21.10|0.138.4+1.21.10|legacy|21"
  "1.21.9|0.134.1+1.21.9|legacy|21"
  "1.21.8|0.136.1+1.21.8|legacy|21"
  "1.21.7|0.129.0+1.21.7|legacy|21"
  "1.21.6|0.128.2+1.21.6|legacy|21"
  "1.21.5|0.128.2+1.21.5|legacy|21"
  "1.21.4|0.119.4+1.21.4|legacy|21"
  "1.21.3|0.114.1+1.21.3|legacy|21"
  "1.21.2|0.106.1+1.21.2|legacy|21"
  "1.21.1|0.116.15+1.21.1|legacy|21"
  "1.21|0.102.0+1.21|legacy|21"
  "26.1|0.145.1+26.1|modern|25"
  "26.1.1|0.145.4+26.1.1|modern|25"
  "26.1.2|0.155.2+26.1.2|modern|25"
  "26.2|0.156.0+26.2|modern|25"
)

build_one() {
  local mc="$1" fapi="$2" style="$3" java_rel="$4"
  echo "=== Building $mc (api $fapi, $style, java $java_rel) ==="
  cat > gradle.properties <<EOF
org.gradle.jvmargs=-Xmx3G
org.gradle.parallel=true

minecraft_version=${mc}
loader_version=${LOADER}
loom_version=${LOOM}
fabric_version=${fapi}
mod_version=${MOD_VERSION}
maven_group=org.twoptwot
archives_base_name=twoptwotvoice
java_release=${java_rel}
loom_style=${style}
EOF

  if [[ "$style" == "modern" ]]; then
    sed -i 's/id '\''fabric-loom'\''/id '\''net.fabricmc.fabric-loom'\''/' build.gradle || true
  else
    sed -i 's/id '\''net.fabricmc.fabric-loom'\''/id '\''fabric-loom'\''/' build.gradle || true
  fi

  # pin fabric.mod.json minecraft dep for this build
  python3 - <<PY
from pathlib import Path
p = Path("src/main/resources/fabric.mod.json")
t = p.read_text()
import re
t = re.sub(r'"minecraft":\s*"[^"]+"', '"minecraft": "~'"$mc"'"', t)
p.write_text(t)
PY

  if ./gradlew --no-daemon clean remapJar -q; then
    jar=$(ls -1 build/libs/twoptwotvoice-*.jar | grep -v sources | grep -v '-dev' | head -1 || true)
    if [[ -z "${jar:-}" ]]; then
      jar=$(ls -1 build/libs/*.jar | grep -v sources | head -1 || true)
    fi
    if [[ -n "${jar:-}" ]]; then
      out="$DIST/twoptwotvoice-${MOD_VERSION}+${mc}.jar"
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
}

: > "$DIST/built.txt"
: > "$DIST/failed.txt"

# restore baseline build.gradle loom id before loop
grep -q "fabric-loom" build.gradle

for entry in "${TARGETS[@]}"; do
  IFS='|' read -r mc fapi style java_rel <<<"$entry"
  build_one "$mc" "$fapi" "$style" "$java_rel" || true
done

# restore 1.21.11 defaults
cat > gradle.properties <<EOF
org.gradle.jvmargs=-Xmx2G
org.gradle.parallel=true

minecraft_version=1.21.11
loader_version=0.19.3
loom_version=1.16.3
fabric_version=0.141.6+1.21.11
mod_version=${MOD_VERSION}
maven_group=org.twoptwot
archives_base_name=twoptwotvoice
EOF
sed -i 's/id '\''net.fabricmc.fabric-loom'\''/id '\''fabric-loom'\''/' build.gradle || true
python3 - <<'PY'
from pathlib import Path
import re
p = Path("src/main/resources/fabric.mod.json")
t = re.sub(r'"minecraft":\s*"[^"]+"', '"minecraft": "~1.21.11"', p.read_text())
p.write_text(t)
PY

echo
echo "Built:"
cat "$DIST/built.txt" || true
echo "Failed:"
cat "$DIST/failed.txt" || true
