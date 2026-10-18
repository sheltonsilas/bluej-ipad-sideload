#!/usr/bin/env bash
# Host self-test for the BlueJ iPad runtime shell.
#
# Installs the interpreted runtime module, builds ci/ios-shell and runs BlueJShell on the
# host JVM so the same entry point that is later compiled for iOS can be checked locally
# and in CI.
set -euo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
MODULE="$(cd "$HERE/.." && pwd)"
ROOT="$(cd "$MODULE/../.." && pwd)"

pathify() { if [ "${WIN_MVN:-0}" = "1" ]; then cygpath -w "$1"; else printf '%s' "$1"; fi; }

if command -v mvn >/dev/null 2>&1; then
  WIN_MVN=0
  MVN=(mvn)
  run_selftest_java() { java -cp "$1" org.bluej.ipad.shell.BlueJShell; }
else
  # Windows host without Maven on PATH: use the portable toolchain next to the workspace.
  WIN_MVN=1
  TOOLS="$ROOT/../ipados-ubuntu/tools"
  MVN=(bash "$TOOLS/mvn.sh")
  JAVA_BIN="$(cygpath -w "$(ls -d "$TOOLS/temurin21"/jdk-* | head -1)")/bin/java.exe"
  run_selftest_java() { "$JAVA_BIN" -cp "$1" org.bluej.ipad.shell.BlueJShell; }
fi

"${MVN[@]}" -B -f "$(pathify "$ROOT/spike-java/pom.xml")" install -Dmaven.test.skip=true -q
"${MVN[@]}" -B -f "$(pathify "$MODULE/pom.xml")" package -q
"${MVN[@]}" -B -f "$(pathify "$MODULE/pom.xml")" dependency:build-classpath \
  -Dmdep.outputFile="$(pathify "$MODULE/target/cp.txt")" -q

DEPS="$(tr -d '\r\n' < "$MODULE/target/cp.txt")"
if [ "$WIN_MVN" = "1" ]; then
  CP="$(cygpath -w "$MODULE/target/classes");$DEPS"
else
  CP="$MODULE/target/classes:$DEPS"
fi
run_selftest_java "$CP"
