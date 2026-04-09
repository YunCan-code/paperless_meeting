#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
source "${SCRIPT_DIR}/common.sh"

require_commands sha256sum

[[ -n "${CHECKSUM_FILE}" && -f "${CHECKSUM_FILE}" ]] || die "未找到 checksums/SHA256SUMS。"

info "开始校验离线包完整性..."
(
  cd "${APP_ROOT}"
  sha256sum -c "${CHECKSUM_FILE}"
)
info "校验完成。"
