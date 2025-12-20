from fastapi import FastAPI
from fastapi.staticfiles import StaticFiles
from fastapi.middleware.cors import CORSMiddleware
import uvicorn
from contextlib import asynccontextmanager

# 导入数据库初始化函数和路由模块
from database import create_db_and_tables
from routes import users, meetings, auth, meeting_types

from fastapi import Request
from fastapi.responses import JSONResponse
from sqlmodel import Session, select
from database import engine
from models import MeetingType

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
            
    yield
    # 关闭时执行 (此处暂无操作)

# 创建 FastAPI 应用实例
app = FastAPI(title="Paperless Meeting System", lifespan=lifespan)

@app.exception_handler(Exception)
async def global_exception_handler(request: Request, exc: Exception):
    import traceback
    error_msg = "".join(traceback.format_exception(None, exc, exc.__traceback__))
    print(f"Global Exception: {error_msg}")
    return JSONResponse(
        status_code=500,
        content={"message": "Internal Server Error", "detail": str(exc), "trace": error_msg},
    )

app.add_middleware(
    CORSMiddleware,
    allow_origins=["http://localhost:5173", "http://127.0.0.1:5173", "http://localhost:8000", "*"], 
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
