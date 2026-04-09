#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

first_existing() {
  local candidate
  for candidate in "$@"; do
    if [[ -n "${candidate}" && -e "${candidate}" ]]; then
      printf '%s\n' "${candidate}"
      return 0
    fi
  done
  return 1
}

discover_app_root() {
  local candidates=(
    "$(cd "${SCRIPT_DIR}/../.." && pwd)"
    "$(cd "${SCRIPT_DIR}/.." && pwd)"
  )
  local candidate
  for candidate in "${candidates[@]}"; do
    if [[ -f "${candidate}/compose.offline.yml" || -f "${candidate}/compose/compose.offline.yml" ]]; then
      printf '%s\n' "${candidate}"
      return 0
    fi
  done
  return 1
}

APP_ROOT="${APP_ROOT:-$(discover_app_root)}"
COMPOSE_FILE="$(first_existing "${APP_ROOT}/compose.offline.yml" "${APP_ROOT}/compose/compose.offline.yml" || true)"
ENV_FILE="$(first_existing "${APP_ROOT}/.env.offline" "${APP_ROOT}/compose/.env.offline" || true)"
ENV_TEMPLATE="$(first_existing "${APP_ROOT}/.env.offline.example" "${APP_ROOT}/compose/.env.offline.example" "${APP_ROOT}/compose.offline.env.example" || true)"
IMAGES_DIR="$(first_existing "${APP_ROOT}/images" || true)"
RUNTIME_DIR="$(first_existing "${APP_ROOT}/runtime" || true)"
CHECKSUM_FILE="$(first_existing "${APP_ROOT}/checksums/SHA256SUMS" || true)"
VERSION_FILE="$(first_existing "${APP_ROOT}/VERSION" || true)"

DATA_DIR="${APP_ROOT}/data"
POSTGRES_DIR="${DATA_DIR}/postgres"
REDIS_DIR="${DATA_DIR}/redis"
UPLOADS_DIR="${DATA_DIR}/uploads"
BACKUP_DIR="${APP_ROOT}/backups"

DB_CONTAINER_NAME="meeting_db"
REDIS_CONTAINER_NAME="meeting_redis"
BACKEND_CONTAINER_NAME="meeting_backend"
FRONTEND_CONTAINER_NAME="meeting_frontend"

info() {
  printf '[INFO] %s\n' "$*"
}

warn() {
  printf '[WARN] %s\n' "$*" >&2
}

die() {
  printf '[ERROR] %s\n' "$*" >&2
  exit 1
}

require_commands() {
  local cmd
  for cmd in "$@"; do
    command -v "${cmd}" >/dev/null 2>&1 || die "缺少命令：${cmd}"
  done
}

require_root() {
  if [[ "${EUID}" -ne 0 ]]; then
    die "请使用 root 用户执行此脚本。"
  fi
}

ensure_compose_file() {
  [[ -n "${COMPOSE_FILE}" && -f "${COMPOSE_FILE}" ]] || die "未找到 compose.offline.yml。"
}

ensure_env_file() {
  if [[ -n "${ENV_FILE}" && -f "${ENV_FILE}" ]]; then
    return 0
  fi

  [[ -n "${ENV_TEMPLATE}" && -f "${ENV_TEMPLATE}" ]] || die "未找到离线环境变量模板。"

  local target_dir
  if [[ -f "${APP_ROOT}/compose/compose.offline.yml" ]]; then
    target_dir="${APP_ROOT}/compose"
  else
    target_dir="${APP_ROOT}"
  fi

  mkdir -p "${target_dir}"
  ENV_FILE="${target_dir}/.env.offline"
  cp "${ENV_TEMPLATE}" "${ENV_FILE}"
  info "已根据模板生成 ${ENV_FILE}"
}

set_env_value() {
  local file="$1"
  local key="$2"
  local value="$3"
  if grep -qE "^${key}=" "${file}" 2>/dev/null; then
    sed -i "s#^${key}=.*#${key}=${value}#g" "${file}"
  else
    printf '%s=%s\n' "${key}" "${value}" >> "${file}"
  fi
}

read_env_value() {
  local key="$1"
  local default_value="${2:-}"
  local value=""

  if [[ -n "${ENV_FILE}" && -f "${ENV_FILE}" ]]; then
    value="$(grep -E "^${key}=" "${ENV_FILE}" | tail -n 1 | cut -d'=' -f2- || true)"
  fi

  if [[ -z "${value}" && -n "${!key-}" ]]; then
    value="${!key}"
  fi

  if [[ -z "${value}" ]]; then
    value="${default_value}"
  fi

  printf '%s' "${value}"
}

ensure_data_dirs() {
  mkdir -p "${POSTGRES_DIR}" "${REDIS_DIR}" "${UPLOADS_DIR}" "${BACKUP_DIR}"
}

ensure_app_root_in_env() {
  ensure_env_file
  set_env_value "${ENV_FILE}" "APP_ROOT" "${APP_ROOT}"
}

docker_compose() {
  ensure_compose_file
  ensure_app_root_in_env
  (
    cd "${APP_ROOT}"
    APP_ROOT="${APP_ROOT}" docker compose --env-file "${ENV_FILE}" -f "${COMPOSE_FILE}" "$@"
  )
}

require_docker() {
  require_commands docker
  docker info >/dev/null 2>&1 || die "Docker 服务不可用，请先启动 Docker。"
  docker compose version >/dev/null 2>&1 || die "Docker Compose 插件不可用。"
}

ensure_default_password_changed() {
  local password
  password="$(read_env_value POSTGRES_PASSWORD CHANGE_ME)"
  if [[ "${password}" == "CHANGE_ME" || "${password}" == "123456" ]]; then
    warn "当前仍在使用示例数据库密码，请尽快修改 compose/.env.offline 中的 POSTGRES_PASSWORD。"
  fi
}

assert_runtime_images_present() {
  local images=()
  local image

  mapfile -t images < <(
    APP_ROOT="${APP_ROOT}" docker compose --env-file "${ENV_FILE}" -f "${COMPOSE_FILE}" config --images
  )

  for image in "${images[@]}"; do
    docker image inspect "${image}" >/dev/null 2>&1 || die "缺少镜像：${image}，请先执行 load-images.sh。"
  done
}

wait_for_postgres() {
  local user db_name
  local attempt

  user="$(read_env_value POSTGRES_USER paperless)"
  db_name="$(read_env_value POSTGRES_DB paperless_meeting)"

  for attempt in $(seq 1 30); do
    if docker exec "${DB_CONTAINER_NAME}" pg_isready -U "${user}" -d "${db_name}" >/dev/null 2>&1; then
      return 0
    fi
    sleep 2
  done

  die "PostgreSQL 在预期时间内未就绪。"
}

safe_empty_dir() {
  local target="$1"
  mkdir -p "${target}"

  local resolved
  resolved="$(cd "${target}" && pwd)"

  case "${resolved}" in
    "${APP_ROOT}/data"|"${APP_ROOT}/data/"*|"${APP_ROOT}/backups"|"${APP_ROOT}/backups/"*)
      ;;
    *)
      die "拒绝清空非应用目录：${resolved}"
      ;;
  esac

  find "${resolved}" -mindepth 1 -maxdepth 1 -exec rm -rf {} +
}

current_release_version() {
  if [[ -n "${VERSION_FILE}" && -f "${VERSION_FILE}" ]]; then
    tr -d '[:space:]' < "${VERSION_FILE}"
    return 0
  fi
  read_env_value APP_VERSION offline-latest
}
