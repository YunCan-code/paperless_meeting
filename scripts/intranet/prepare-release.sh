#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
source "${SCRIPT_DIR}/common.sh"

usage() {
  cat <<EOF
用法:
  ./prepare-release.sh <version>

示例:
  ./prepare-release.sh 2026.04.08
EOF
}

run_smoke_test() {
  local version="$1"
  local smoke_root="${APP_ROOT}/.temp/offline-smoke/${version}"
  local smoke_env="${smoke_root}/.env.offline"
  local smoke_port="${SMOKE_PORT:-5500}"

  mkdir -p "${smoke_root}/data/postgres" "${smoke_root}/data/redis" "${smoke_root}/data/uploads" "${smoke_root}/backups"
  cp "${APP_ROOT}/compose.offline.env.example" "${smoke_env}"

  set_env_value "${smoke_env}" "APP_ROOT" "${smoke_root}"
  set_env_value "${smoke_env}" "APP_VERSION" "${version}"
  set_env_value "${smoke_env}" "BACKEND_IMAGE" "paperless-meeting/backend:${version}"
  set_env_value "${smoke_env}" "FRONTEND_IMAGE" "paperless-meeting/frontend:${version}"
  set_env_value "${smoke_env}" "FRONTEND_PORT" "${smoke_port}"
  set_env_value "${smoke_env}" "POSTGRES_PASSWORD" "offline_smoke_test"
  set_env_value "${smoke_env}" "CORS_ORIGINS" "http://127.0.0.1:${smoke_port},http://localhost:${smoke_port}"

  info "执行本地冒烟验证，端口 ${smoke_port} ..."
  APP_ROOT="${smoke_root}" docker compose --env-file "${smoke_env}" -f "${APP_ROOT}/compose.offline.yml" up -d

  local attempt
  for attempt in $(seq 1 30); do
    if curl -fsS "http://127.0.0.1:${smoke_port}/" >/dev/null 2>&1 && \
       curl -fsS "http://127.0.0.1:${smoke_port}/api/docs" >/dev/null 2>&1; then
      info "冒烟验证通过。"
      break
    fi
    sleep 2
    if [[ "${attempt}" -eq 30 ]]; then
      APP_ROOT="${smoke_root}" docker compose --env-file "${smoke_env}" -f "${APP_ROOT}/compose.offline.yml" logs
      APP_ROOT="${smoke_root}" docker compose --env-file "${smoke_env}" -f "${APP_ROOT}/compose.offline.yml" down -v
      rm -rf "${smoke_root}"
      die "冒烟验证失败，请检查镜像或运行日志。"
    fi
  done

  APP_ROOT="${smoke_root}" docker compose --env-file "${smoke_env}" -f "${APP_ROOT}/compose.offline.yml" down -v
  rm -rf "${smoke_root}"
}

generate_checksums() {
  local release_root="$1"
  local checksum_file="${release_root}/checksums/SHA256SUMS"

  mkdir -p "${release_root}/checksums"
  (
    cd "${release_root}"
    find . -type f ! -path './checksums/SHA256SUMS' -print0 | sort -z | xargs -0 sha256sum > "${checksum_file}"
  )
}

version="${1:-}"
[[ -n "${version}" ]] || { usage; die "请提供发布版本号。"; }

require_commands docker sha256sum tar curl
ensure_compose_file

release_root="${APP_ROOT}/release/${version}"
archive_path="${APP_ROOT}/release/paperless-meeting-offline-${version}.tar.gz"

rm -rf "${release_root}"
mkdir -p "${release_root}/images" "${release_root}/compose" "${release_root}/scripts" "${release_root}/docs" "${release_root}/runtime/docker/debs"

info "构建业务镜像..."
docker build -t "paperless-meeting/backend:${version}" "${APP_ROOT}/backend"
docker build -t "paperless-meeting/frontend:${version}" "${APP_ROOT}/frontend"

info "拉取基础镜像..."
docker pull "postgres:15-alpine"
docker pull "redis:7-alpine"

if [[ "${SKIP_SMOKE_TEST:-0}" != "1" ]]; then
  run_smoke_test "${version}"
else
  warn "已跳过冒烟验证（SKIP_SMOKE_TEST=1）。"
fi

info "导出镜像 tar 包..."
docker save -o "${release_root}/images/paperless-meeting-backend-${version}.tar" "paperless-meeting/backend:${version}"
docker save -o "${release_root}/images/paperless-meeting-frontend-${version}.tar" "paperless-meeting/frontend:${version}"
docker save -o "${release_root}/images/postgres-15-alpine.tar" "postgres:15-alpine"
docker save -o "${release_root}/images/redis-7-alpine.tar" "redis:7-alpine"

info "复制离线部署文件..."
cp "${APP_ROOT}/compose.offline.yml" "${release_root}/compose/compose.offline.yml"
cp "${APP_ROOT}/compose.offline.env.example" "${release_root}/compose/.env.offline"
cp "${SCRIPT_DIR}/common.sh" "${release_root}/scripts/common.sh"
cp "${SCRIPT_DIR}/install-offline.sh" "${release_root}/scripts/install-offline.sh"
cp "${SCRIPT_DIR}/load-images.sh" "${release_root}/scripts/load-images.sh"
cp "${SCRIPT_DIR}/start.sh" "${release_root}/scripts/start.sh"
cp "${SCRIPT_DIR}/stop.sh" "${release_root}/scripts/stop.sh"
cp "${SCRIPT_DIR}/status.sh" "${release_root}/scripts/status.sh"
cp "${SCRIPT_DIR}/backup.sh" "${release_root}/scripts/backup.sh"
cp "${SCRIPT_DIR}/restore.sh" "${release_root}/scripts/restore.sh"
cp "${SCRIPT_DIR}/upgrade.sh" "${release_root}/scripts/upgrade.sh"
cp "${SCRIPT_DIR}/verify-checksums.sh" "${release_root}/scripts/verify-checksums.sh"
cp "${APP_ROOT}/doc/内网离线部署.md" "${release_root}/docs/内网离线部署.md"
cp "${SCRIPT_DIR}/runtime.README.md" "${release_root}/runtime/README.md"

shopt -s nullglob
runtime_debs=("${APP_ROOT}"/runtime/docker/debs/*.deb)
shopt -u nullglob
if [[ ${#runtime_debs[@]} -gt 0 ]]; then
  info "复制 Docker 运行时离线包..."
  cp "${runtime_debs[@]}" "${release_root}/runtime/docker/debs/"
else
  warn "未发现 runtime/docker/debs/*.deb，生成的发布包将不包含 Docker 离线安装包。"
fi

set_env_value "${release_root}/compose/.env.offline" "APP_ROOT" "/opt/paperless_meeting"
set_env_value "${release_root}/compose/.env.offline" "APP_VERSION" "${version}"
set_env_value "${release_root}/compose/.env.offline" "BACKEND_IMAGE" "paperless-meeting/backend:${version}"
set_env_value "${release_root}/compose/.env.offline" "FRONTEND_IMAGE" "paperless-meeting/frontend:${version}"

printf '%s\n' "${version}" > "${release_root}/VERSION"

generate_checksums "${release_root}"

find "${release_root}/scripts" -type f -name '*.sh' -exec chmod +x {} +

info "生成离线发布压缩包..."
tar -C "${APP_ROOT}/release" -czf "${archive_path}" "${version}"

info "离线发布包已生成：${release_root}"
info "压缩包路径：${archive_path}"
