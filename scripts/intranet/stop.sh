#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
source "${SCRIPT_DIR}/common.sh"

require_docker
ensure_app_root_in_env

info "停止离线部署服务..."
docker_compose stop
info "服务已停止。"
