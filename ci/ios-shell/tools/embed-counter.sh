#!/usr/bin/env bash
# Compile ci/ios-shell/example/bluej/example/Counter.java with the portable Temurin 21 JDK
# and embed the resulting class file as base64 inside BlueJShell.java.
#
# The iOS build has no JVM compiler at runtime yet, so the interpreted bytecode is
# checked in as a base64 constant. Re-run this script after editing the example source.
set -euo pipefail
export MSYS_NO_PATHCONV=1

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
MODULE="$(cd "$HERE/.." && pwd)"
if [ -n "${JDK_HOME:-}" ]; then
  JDK_W="$(cygpath -w "$JDK_HOME")"
else
  TOOLS_DIR="$MODULE/../../../ipados-ubuntu/tools/temurin21"
  JDK_W="$(cygpath -w "$(ls -d "$TOOLS_DIR"/jdk-* | head -1)")"
fi
echo "using JDK: $JDK_W"
TARGET="$MODULE/.build-counter"
SHELL_JAVA="$MODULE/src/main/java/org/bluej/ipad/shell/BlueJShell.java"

rm -rf "$TARGET"
mkdir -p "$TARGET"
"$JDK_W/bin/javac.exe" --release 21 -d "$(cygpath -w "$TARGET")" "$(cygpath -w "$MODULE/example/bluej/example/Counter.java")"
base64 -w0 "$TARGET/bluej/example/Counter.class" > "$TARGET/counter.b64"

b64="$(tr -d '\r\n' < "$TARGET/counter.b64")"
case "$b64" in
  "" ) echo "ERROR: empty base64 payload" >&2; exit 1 ;;
esac
# Replace the existing constant value (or the placeholder) with the freshly compiled bytes.
sed -i -E "s|(COUNTER_CLASS_BASE64 = \")[A-Za-z0-9+/_=]*\";|\1${b64}\";|" "$SHELL_JAVA"
grep -q "COUNTER_CLASS_BASE64 = \"${b64}\";" "$SHELL_JAVA"
echo "embedded ${#b64} base64 characters into $SHELL_JAVA"
