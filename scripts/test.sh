#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
MAIN_OUT_DIR="$ROOT_DIR/out/classes"
TEST_OUT_DIR="$ROOT_DIR/out/test-classes"

"$ROOT_DIR/scripts/build.sh"
rm -rf "$TEST_OUT_DIR"
mkdir -p "$TEST_OUT_DIR"
find "$ROOT_DIR/src/test/java" -name '*.java' -print0 | xargs -0 javac --release 21 -cp "$MAIN_OUT_DIR" -d "$TEST_OUT_DIR"
java -cp "$MAIN_OUT_DIR:$TEST_OUT_DIR" lab.audit.AuditJobRunnerTest
