#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
source "${SCRIPT_DIR}/common.sh"

require_docker

[[ -n "${IMAGES_DIR}" && -d "${IMAGES_DIR}" ]] || die "未找到 images 目录。"

shopt -s nullglob
image_files=("${IMAGES_DIR}"/*.tar)
shopt -u nullglob

[[ ${#image_files[@]} -gt 0 ]] || die "images 目录中没有可导入的 tar 镜像。"

for image_file in "${image_files[@]}"; do
  info "导入镜像 $(basename "${image_file}") ..."
  docker load -i "${image_file}"
done

info "镜像导入完成。"
