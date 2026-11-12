#!/usr/bin/env bash
# Fetch the pinned whisper.cpp source (v1.8.4) into labs/v1/.deps/whisper.cpp; verify the exact commit.
set -euo pipefail
root="$(cd -- "$(dirname -- "$0")/.." && pwd)"
commit=9386f239401074690479731c1e41683fbbeac557
dir="$root/.deps/whisper.cpp"
if [ ! -d "$dir/.git" ]; then mkdir -p "$dir"; git -C "$dir" init -q; fi
if [ "$(git -C "$dir" rev-parse HEAD 2>/dev/null || true)" != "$commit" ]; then
  git -C "$dir" fetch -q --depth 1 https://github.com/ggml-org/whisper.cpp "$commit"
  git -C "$dir" checkout -q --detach FETCH_HEAD
fi
test "$(git -C "$dir" rev-parse HEAD)" = "$commit"
echo "whisper.cpp $commit ready in $dir"
