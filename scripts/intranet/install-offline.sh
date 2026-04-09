#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
source "${SCRIPT_DIR}/common.sh"

install_docker_offline() {
  if command -v docker >/dev/null 2>&1; then
    info "检测到 Docker 已安装，跳过离线安装。"
    return 0
  fi

  [[ -n "${RUNTIME_DIR}" && -d "${RUNTIME_DIR}/docker/debs" ]] || die "未找到 runtime/docker/debs 离线安装包目录。"

  shopt -s nullglob
  deb_files=("${RUNTIME_DIR}"/docker/debs/*.deb)
  shopt -u nullglob

  [[ ${#deb_files[@]} -gt 0 ]] || die "runtime/docker/debs 中没有 .deb 安装包。"

  info "开始安装 Docker 运行时离线包..."
  dpkg -i "${deb_files[@]}"
  systemctl enable --now docker
  docker compose version >/dev/null 2>&1 || die "Docker Compose 插件不可用，请检查离线包是否完整。"
}

require_root
require_commands dpkg systemctl sha256sum

ensure_data_dirs
ensure_app_root_in_env

if [[ -n "${CHECKSUM_FILE}" && -f "${CHECKSUM_FILE}" ]]; then
  "${SCRIPT_DIR}/verify-checksums.sh"
else
  warn "未找到 SHA256SUMS，跳过完整性校验。"
fi

install_docker_offline
"${SCRIPT_DIR}/load-images.sh"
"${SCRIPT_DIR}/start.sh"

info "离线安装完成。"
