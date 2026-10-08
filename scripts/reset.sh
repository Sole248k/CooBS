#!/usr/bin/env bash
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"

echo "Resetting Local Data Foundation (wiping volumes & fresh re-initialization)..."
(cd "${ROOT_DIR}" && docker compose down -v)
bash "${SCRIPT_DIR}/setup.sh"
