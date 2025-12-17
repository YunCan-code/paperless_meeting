from fastapi import FastAPI
from fastapi.staticfiles import StaticFiles
from fastapi.middleware.cors import CORSMiddleware
import uvicorn
from contextlib import asynccontextmanager

# 导入数据库初始化函数和路由模块
from database import create_db_and_tables
from routes import users, meetings, auth, meeting_types

# 定义应用生命周期管理器
@asynccontextmanager
async def lifespan(app: FastAPI):
    # 启动时执行: 创建数据库表
    create_db_and_tables()
    yield
    # 关闭时执行 (此处暂无操作)

# 创建 FastAPI 应用实例
app = FastAPI(title="Paperless Meeting System", lifespan=lifespan)

# 配置 CORS (跨域资源共享)
# 允许局域网内所有设备访问
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"], # 允许所有来源
    allow_credentials=True,
    allow_methods=["*"], # 允许所有 HTTP 方法
    allow_headers=["*"], # 允许所有 HTTP 头
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
