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

# Worker 数量说明:
# 由于使用了 Socket.IO（WebSocket），需要保持长连接状态
# 多 Worker 模式下 Socket.IO 会话不共享，会导致连接不稳定
# 因此这里使用单 Worker 模式
# 如果需要多 Worker，需要配置 Redis 作为 Socket.IO 的消息队列
WORKERS=1

echo "Starting Paperless Meeting Backend with $WORKERS worker (Socket.IO mode)..."

# 使用 gunicorn 启动 uvicorn worker
# --bind: 绑定地址和端口
# --workers: 工作进程数（Socket.IO 需要单进程或配合 Redis）
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

