#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

MOD_VERSION="$(grep '^mod_version=' gradle.properties | cut -d= -f2)"
DIST="$ROOT/dist"
mkdir -p "$DIST"

export JAVA_HOME="${JAVA_HOME:-/usr/lib/jvm/java-21-openjdk-amd64}"
export PATH="$JAVA_HOME/bin:$PATH"

# Keep loader version in sync with the voice mod release.
sed -i "s/^mod_version=.*/mod_version=${MOD_VERSION}/" loader/gradle.properties

echo "=== Building voice loader ${MOD_VERSION} ==="
(
  cd loader
  ./gradlew clean build --no-daemon -q
)

jar=$(ls -1 loader/build/libs/twoptwotvoice-loader-*.jar 2>/dev/null | grep -v sources | grep -v -- '-dev' | head -1 || true)
if [[ -z "$jar" ]]; then
  echo "Loader jar missing after build" >&2
  exit 1
fi

out="$DIST/twoptwotvoice-loader-${MOD_VERSION}.jar"
cp -f "$jar" "$out"
echo "OK $out"
ls -lh "$out"
