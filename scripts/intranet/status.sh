#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
source "${SCRIPT_DIR}/common.sh"

require_docker
ensure_app_root_in_env

printf '应用目录: %s\n' "${APP_ROOT}"
printf '当前版本: %s\n' "$(current_release_version)"
printf '访问端口: %s\n' "$(read_env_value FRONTEND_PORT 5000)"
printf '前端镜像: %s\n' "$(read_env_value FRONTEND_IMAGE paperless-meeting/frontend:offline-latest)"
printf '后端镜像: %s\n' "$(read_env_value BACKEND_IMAGE paperless-meeting/backend:offline-latest)"

echo
docker_compose ps

echo
if [[ -d "${BACKUP_DIR}" ]]; then
  latest_backup="$(find "${BACKUP_DIR}" -mindepth 1 -maxdepth 1 -type d | sort | tail -n 1 || true)"
  if [[ -n "${latest_backup}" ]]; then
    printf '最近备份: %s\n' "${latest_backup}"
  else
    echo '最近备份: 无'
  fi
else
  echo '最近备份: 无'
fi

echo
printf '访问地址: http://<会议室主机IP>:%s\n' "$(read_env_value FRONTEND_PORT 5000)"
