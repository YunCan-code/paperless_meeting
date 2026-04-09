#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
source "${SCRIPT_DIR}/common.sh"

require_docker
ensure_data_dirs
ensure_app_root_in_env

label="${1:-manual}"
timestamp="$(date '+%Y%m%d-%H%M%S')"
backup_name="${timestamp}-${label}"
backup_path="${BACKUP_DIR}/${backup_name}"

mkdir -p "${backup_path}"

info "确保数据库与 Redis 已启动..."
docker_compose up -d db redis
wait_for_postgres

db_user="$(read_env_value POSTGRES_USER paperless)"
db_name="$(read_env_value POSTGRES_DB paperless_meeting)"
db_password="$(read_env_value POSTGRES_PASSWORD CHANGE_ME)"

info "导出 PostgreSQL 数据库..."
docker exec -e "PGPASSWORD=${db_password}" "${DB_CONTAINER_NAME}" \
  pg_dump -U "${db_user}" -d "${db_name}" -Fc > "${backup_path}/database.dump"

info "打包 uploads 目录..."
tar -C "${UPLOADS_DIR}" -czf "${backup_path}/uploads.tar.gz" .

cp "${COMPOSE_FILE}" "${backup_path}/compose.offline.yml"
cp "${ENV_FILE}" "${backup_path}/.env.offline"

cat > "${backup_path}/metadata.txt" <<EOF
backup_name=${backup_name}
created_at=$(date '+%Y-%m-%d %H:%M:%S')
app_root=${APP_ROOT}
app_version=$(current_release_version)
frontend_image=$(read_env_value FRONTEND_IMAGE paperless-meeting/frontend:offline-latest)
backend_image=$(read_env_value BACKEND_IMAGE paperless-meeting/backend:offline-latest)
EOF

info "备份完成：${backup_path}"
