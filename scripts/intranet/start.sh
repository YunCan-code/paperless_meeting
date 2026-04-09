#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
source "${SCRIPT_DIR}/common.sh"

require_docker
ensure_data_dirs
ensure_app_root_in_env
ensure_default_password_changed
assert_runtime_images_present

info "启动离线部署服务..."
docker_compose up -d
info "服务已启动。"
