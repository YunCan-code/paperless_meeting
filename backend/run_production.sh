#!/bin/bash
# ============================================================
# 生产环境启动脚本 - 使用 Gunicorn 多 Worker 模式
# ============================================================
# 
# 使用方法:
# 1. chmod +x run_production.sh
# 2. ./run_production.sh
#
# 或者使用 systemd 管理服务 (推荐)
# ============================================================

# 进入后端目录
cd "$(dirname "$0")"

# Worker 数量建议: CPU核数 * 2 + 1
# 例如 4 核 CPU 使用 9 个 Worker
# 使用 Redis 作为 Socket.IO 消息队列，支持多 Worker 模式
WORKERS=${WORKERS:-4}

echo "Starting Paperless Meeting Backend with $WORKERS workers..."

# 使用 gunicorn 启动 uvicorn worker
# --bind: 绑定地址和端口
# --workers: 工作进程数
# --worker-class: 使用 uvicorn 的异步 worker
# --access-logfile: 访问日志
# --error-logfile: 错误日志
# --keep-alive: 长连接保持时间（秒），WebSocket 需要较长时间
# --timeout: 请求超时时间
gunicorn main:app \
    --bind 0.0.0.0:8000 \
    --workers $WORKERS \
    --worker-class uvicorn.workers.UvicornWorker \
    --access-logfile - \
    --error-logfile - \
    --forwarded-allow-ips "*" \
    --timeout 300 \
    --keep-alive 120
