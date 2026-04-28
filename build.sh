#!/usr/bin/env bash
# build.sh — compile and optionally run the Linux monitor
set -euo pipefail

SRC_DIR="src"
OUT_DIR="out"
MAIN_CLASS="com.agent.server.MetricsHttpServer"

echo "==> Compiling..."
mkdir -p "$OUT_DIR"
javac --release 17 -d "$OUT_DIR" $(find "$SRC_DIR" -name "*.java")
echo "==> Done. $(find "$OUT_DIR" -name '*.class' | wc -l) class files."

if [[ "${1:-}" == "run" ]]; then
    echo "==> Starting server on :8000 (Ctrl-C to stop)"
    java -cp "$OUT_DIR" "$MAIN_CLASS"
fi
