#!/bin/zsh
set -e
cd -- "$(dirname -- "$0")"
preview_node="$(command -v node || true)"
if [[ -z "$preview_node" ]]; then
  preview_node="$HOME/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/bin/node"
fi
if [[ ! -x "$preview_node" ]]; then
  print '需要 Node.js 22 或更高版本。安装后重新运行此文件。'
  exit 1
fi
exec "$preview_node" server.mjs
