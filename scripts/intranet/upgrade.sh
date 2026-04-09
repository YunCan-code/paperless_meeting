#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
source "${SCRIPT_DIR}/common.sh"

require_docker
ensure_data_dirs
ensure_app_root_in_env

target_version="${1:-$(current_release_version)}"
[[ -n "${target_version}" ]] || die "无法确定升级目标版本。"

info "开始升级到版本 ${target_version} ..."

"${SCRIPT_DIR}/backup.sh" "pre-upgrade-${target_version}"
"${SCRIPT_DIR}/load-images.sh"

set_env_value "${ENV_FILE}" "APP_VERSION" "${target_version}"
set_env_value "${ENV_FILE}" "BACKEND_IMAGE" "paperless-meeting/backend:${target_version}"
set_env_value "${ENV_FILE}" "FRONTEND_IMAGE" "paperless-meeting/frontend:${target_version}"

assert_runtime_images_present

docker_compose up -d db redis
wait_for_postgres
docker_compose up -d --force-recreate backend frontend

info "升级完成，当前版本已切换到 ${target_version}。"
