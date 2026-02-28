from fastapi import FastAPI
from fastapi.staticfiles import StaticFiles
from fastapi.middleware.cors import CORSMiddleware
import uvicorn
import os
from contextlib import asynccontextmanager

# 导入数据库初始化函数和路由模块
from database import create_db_and_tables
from routes import users, meetings, auth, meeting_types, notes, devices, app_updates, system_settings, sync, vote, lottery, reading_progress, checkin, dashboard
from socket_manager import sio, socket_app

from fastapi import Request
from fastapi.responses import JSONResponse
from sqlmodel import Session, select
from database import engine
from models import MeetingType

# 调试模式：生产环境设为 false，避免泄露 traceback
DEBUG = os.getenv("DEBUG", "false").lower() in ("true", "1", "yes")

# 定义应用生命周期管理器
@asynccontextmanager
async def lifespan(app: FastAPI):
    # 启动时执行: 创建数据库表
    create_db_and_tables()
    
    # 检查并创建默认会议类型
    with Session(engine) as session:
        if not session.exec(select(MeetingType)).first():
            print("初始化默认会议类型...")
            session.add(MeetingType(name="党委会", description="默认类型"))
            session.commit()
    
    # 启动自动关闭过期投票的后台任务
    import asyncio
    from vote_auto_closer import auto_close_expired_votes
    auto_close_task = asyncio.create_task(auto_close_expired_votes())
    print("[STARTUP] 已启动投票自动关闭任务")
    
    yield
    
    # 关闭时取消后台任务
    auto_close_task.cancel()
    try:
        await auto_close_task
    except asyncio.CancelledError:
        print("[SHUTDOWN] 投票自动关闭任务已停止")

# 创建 FastAPI 应用实例
app = FastAPI(title="Paperless Meeting System", lifespan=lifespan)

@app.exception_handler(Exception)
async def global_exception_handler(request: Request, exc: Exception):
    import traceback
    error_msg = "".join(traceback.format_exception(None, exc, exc.__traceback__))
    print(f"Global Exception: {error_msg}")
    if DEBUG:
        # 开发环境：返回详细错误信息便于调试
        return JSONResponse(
            status_code=500,
            content={"message": "Internal Server Error", "detail": str(exc), "trace": error_msg},
        )
    else:
        # 生产环境：只返回通用错误消息，traceback 仅输出到日志
        return JSONResponse(
            status_code=500,
            content={"message": "Internal Server Error"},
        )

# CORS 配置：通过环境变量指定允许的前端域名，多个用逗号分隔
CORS_ORIGINS = os.getenv(
    "CORS_ORIGINS",
    "http://localhost:5173,http://127.0.0.1:5173,http://localhost:8000"
).split(",")

app.add_middleware(
    CORSMiddleware,
    allow_origins=[o.strip() for o in CORS_ORIGINS],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# 挂载静态文件目录
# 用于让平版端可以通过 URL (如 http://ip:8000/static/file.pdf) 访问上传的文件
app.mount("/static", StaticFiles(directory="uploads"), name="static")

# 注册 API 路由器
app.include_router(users.router)
app.include_router(meetings.router)
app.include_router(auth.router)
app.include_router(meeting_types.router)
app.include_router(notes.router)
app.include_router(devices.router)
app.include_router(app_updates.router)
app.include_router(system_settings.router)
app.include_router(sync.router, prefix="/sync", tags=["Meeting Sync"])
app.include_router(vote.router)
app.include_router(lottery.router)
app.include_router(reading_progress.router)
app.include_router(checkin.router)
app.include_router(dashboard.router)

# 挂载 Socket.IO (WebSocket 端点位于 /socket.io/)
app.mount("/socket.io", socket_app)

@app.get("/")
def read_root():
    """
    根路径测试接口
    """
    return {"message": "Meeting System Backend API is running"}

if __name__ == "__main__":
    # 启动开发服务器
    # host="0.0.0.0" 表示监听所有网卡，允许局域网访问
    uvicorn.run("main:app", host="0.0.0.0", port=8000, reload=True)
