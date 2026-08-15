#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
OUT_DIR="$ROOT_DIR/out/classes"

rm -rf "$OUT_DIR"
mkdir -p "$OUT_DIR"
find "$ROOT_DIR/src/main/java" -name '*.java' -print0 | xargs -0 javac --release 21 -d "$OUT_DIR"
