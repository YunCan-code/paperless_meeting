#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
source "${SCRIPT_DIR}/common.sh"

usage() {
  cat <<EOF
用法:
  ./restore.sh <备份目录> [--yes]
EOF
}

require_docker
ensure_data_dirs
ensure_app_root_in_env

backup_path="${1:-}"
confirm_flag="${2:-}"

[[ -n "${backup_path}" ]] || { usage; die "请提供要恢复的备份目录。"; }

if [[ ! -d "${backup_path}" ]]; then
  die "备份目录不存在：${backup_path}"
fi

if [[ "${confirm_flag}" != "--yes" ]]; then
  read -r -p "恢复会覆盖当前数据库和上传文件，确认继续吗？[y/N] " reply
  if [[ ! "${reply}" =~ ^[Yy]$ ]]; then
    info "已取消恢复。"
    exit 0
  fi
fi

database_dump="${backup_path}/database.dump"
uploads_archive="${backup_path}/uploads.tar.gz"

[[ -f "${database_dump}" ]] || die "缺少数据库备份文件：${database_dump}"

info "先做一次恢复前备份..."
"${SCRIPT_DIR}/backup.sh" pre-restore

info "停止前端和后端，避免写入冲突..."
docker_compose stop frontend backend || true
docker_compose up -d db redis
wait_for_postgres

db_user="$(read_env_value POSTGRES_USER paperless)"
db_name="$(read_env_value POSTGRES_DB paperless_meeting)"
db_password="$(read_env_value POSTGRES_PASSWORD CHANGE_ME)"

info "重建数据库 ${db_name} ..."
docker exec -e "PGPASSWORD=${db_password}" "${DB_CONTAINER_NAME}" dropdb -U "${db_user}" --if-exists "${db_name}"
docker exec -e "PGPASSWORD=${db_password}" "${DB_CONTAINER_NAME}" createdb -U "${db_user}" "${db_name}"

info "恢复数据库内容..."
cat "${database_dump}" | docker exec -i -e "PGPASSWORD=${db_password}" "${DB_CONTAINER_NAME}" \
  pg_restore -U "${db_user}" -d "${db_name}" --clean --if-exists --no-owner --no-privileges

if [[ -f "${uploads_archive}" ]]; then
  info "恢复上传文件..."
  safe_empty_dir "${UPLOADS_DIR}"
  tar -xzf "${uploads_archive}" -C "${UPLOADS_DIR}"
else
  warn "未找到 uploads.tar.gz，跳过文件恢复。"
fi

docker_compose up -d
info "恢复完成。"
