import os
from sqlmodel import SQLModel, create_engine, Session

# ============================================================
# 数据库配置 - 支持 SQLite (开发) 和 PostgreSQL (生产)
# ============================================================

# 通过环境变量配置数据库URL
# 开发环境默认使用 SQLite
# 生产环境设置 DATABASE_URL 环境变量切换到 PostgreSQL
# 例如: DATABASE_URL=postgresql://user:password@localhost:5432/paperless_meeting

DATABASE_URL = os.getenv("DATABASE_URL", None)

if DATABASE_URL is None:
    # 默认: SQLite (开发环境)
    BASE_DIR = os.path.dirname(os.path.abspath(__file__))
    sqlite_file_name = os.path.join(BASE_DIR, "database.db")
    DATABASE_URL = f"sqlite:///{sqlite_file_name}"
    
    # SQLite 特殊配置
    connect_args = {"check_same_thread": False}
    engine = create_engine(DATABASE_URL, connect_args=connect_args)
    
elif "sqlite" in DATABASE_URL:
    # 显式指定 SQLite
    connect_args = {"check_same_thread": False}
    engine = create_engine(DATABASE_URL, connect_args=connect_args)
    
else:
    # PostgreSQL / MySQL 生产环境配置
    # 连接池优化: 支持高并发
    engine = create_engine(
        DATABASE_URL,
        pool_size=20,         # 保持的连接数
        max_overflow=10,      # 最大溢出连接数
        pool_timeout=30,      # 获取连接超时时间(秒)
        pool_recycle=1800,    # 连接回收时间(秒), 防止数据库断连
        pool_pre_ping=True    # 使用前检测连接是否有效
    )

def create_db_and_tables():
    """
    创建数据库和表结构
    如果没有表会自动创建，有的话会跳过
    """
    try:
        SQLModel.metadata.create_all(engine)
    except Exception as e:
        # 在多 worker 启动时，可能会遇到并发创建表的竞争条件
        # 如果甚至 "UniqueViolation" 等错误，通常意味着另一个 worker 已经创建了表
        print(f"[WARN] Database creation warning (likely race condition): {e}")

def get_session():
    """
    获取数据库会话 (Dependency Injection)
    用于在每个请求中提供一个独立的数据库会话，请求结束自动关闭
    """
    with Session(engine) as session:
        yield session
