# Paperless Meeting System

无纸化会议系统，覆盖 Web 管理端、Android 客户端与大屏展示，支持会议全流程数字化：会前管理、会中阅读/投票/抽签、会后统计分析。

当前仓库用于实际开发与部署验证，整体可运行，但部分能力仍在持续迭代。

## 1. 核心功能

- 会议管理：创建/编辑/删除会议，支持会议类型、参会人角色、会议信息维护。
- 文件管理：上传 PDF 附件，会议详情中统一管理与阅读。
- 用户与权限相关能力：用户管理、登录、修改密码（当前认证为轻量实现）。
- 投票系统：创建投票、实时开始/更新/结束、结果统计、大屏展示。
- 抽签系统：会议内抽签轮次管理、历史查看、状态同步。
- 同步阅读：阅读进度记录、会议阅读同步状态。
- 设备管理：设备心跳、远程指令下发与回执。
- 签到与看板：签到记录、个人统计数据看板（协作、类型分布、热力图等）。
- 安卓升级：后端提供版本信息与下载地址，前端落地页支持二维码下载。

## 2. 系统架构

`Frontend (Vue3) + Android (Compose)`
`        |`
`   HTTP/Socket.IO`
`        |`
`Backend (FastAPI + SQLModel)`
`        |`
`PostgreSQL / SQLite + Redis`

- Web 管理端与大屏：Vue 3 + Vite + Element Plus + ECharts。
- Android 客户端：Kotlin + Jetpack Compose + Hilt + Retrofit + Socket.IO。
- 后端：FastAPI + SQLModel，按路由模块组织业务。
- 实时通信：Socket.IO（配合 Redis，支持多 worker 场景）。
- 存储：开发可用 SQLite，生产建议 PostgreSQL。

## 3. 技术栈

### Frontend

- Vue 3
- Vite
- Element Plus
- Axios
- socket.io-client
- ECharts

### Backend

- FastAPI
- SQLModel / SQLAlchemy
- Uvicorn / Gunicorn
- python-socketio
- Redis
- PostgreSQL / SQLite

### Android

- Kotlin + Jetpack Compose
- Hilt + KSP
- Retrofit + OkHttp
- WorkManager
- Socket.IO client

## 4. 目录结构

```text
paperless_meeting/
├─ frontend/      # Web 管理端 + 大屏页面
├─ backend/       # FastAPI 服务端
├─ android/       # Android 客户端
├─ doc/           # 设计与部署文档
├─ docker-compose.yml
└─ README.md
```

## 5. 快速开始（推荐：Docker）

### 5.1 前置条件

- Docker
- Docker Compose

### 5.2 启动

1. 修改 `docker-compose.yml` 中的数据库密码（务必不要使用默认弱口令）。
2. 在项目根目录执行：

```bash
docker compose up -d --build
```

3. 默认访问地址：

- 前端入口：`http://localhost:5000`
- 后端 API 文档：`http://localhost:5000/api/docs`

### 5.3 容器说明

- `frontend`：Nginx 托管前端静态资源，并反向代理 `/api`、`/static`、`/socket.io` 到后端。
- `backend`：FastAPI 服务，生产模式可使用 `run_production.sh` 以 Gunicorn 多 worker 启动。
- `db`：PostgreSQL 15。
- `redis`：Socket.IO 跨进程消息支持。

## 6. 本地开发（前后端分离）

### 6.1 Backend

```bash
cd backend
pip install -r requirements.txt
python -m uvicorn main:app --host 0.0.0.0 --port 8001 --reload
```

Windows 可直接运行：

```powershell
cd backend
.\run.bat
```

### 6.2 Frontend

```bash
cd frontend
npm install
npm run dev
```

默认开发地址：`http://localhost:5173`  
开发代理默认转发到 `http://127.0.0.1:8001`。

## 7. Android 客户端

- 代码目录：`android/`
- 接口地址配置：`android/app/build.gradle.kts` 中 `API_BASE_URL`、`SOCKET_BASE_URL`、`STATIC_BASE_URL`。

常用构建命令：

```powershell
cd android
.\gradlew.bat assembleDebug
```

## 8. 主要后端模块

- `/auth`：登录
- `/users`：用户管理、导入、密码修改
- `/meetings`：会议管理、附件上传、统计
- `/meeting_types`：会议类型与封面图
- `/vote`：投票创建/启动/提交/结果
- `/lottery`：抽签与历史
- `/devices`：设备心跳、指令
- `/reading-progress`：阅读进度
- `/checkin`：签到与补签
- `/dashboard`：个人看板数据
- `/updates`：Android 版本更新信息
- `/sync`：会议阅读同步状态

## 9. 注意事项

- 默认配置中存在示例密码，仅供开发测试，部署前务必全部替换。
- 当前认证机制较轻量，生产环境建议补充完整鉴权与权限模型（JWT、RBAC、审计日志等）。
- 上传文件目前主要围绕 PDF 流程设计，若需扩展文件类型建议同步更新前后端校验逻辑。

## 10. 相关文档

- 部署说明：`doc/部署.md`
- 其他功能设计与优化记录：`doc/`

---

如果你是首次接手本项目，建议先按 “Docker 快速开始” 跑通系统，再阅读 `backend/routes` 与 `frontend/src/views` 快速理解业务流程。
