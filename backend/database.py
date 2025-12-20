import os
from sqlmodel import SQLModel, create_engine, Session

# Define SQLite database path relative to this file
BASE_DIR = os.path.dirname(os.path.abspath(__file__))
sqlite_file_name = os.path.join(BASE_DIR, "database.db")
sqlite_url = f"sqlite:///{sqlite_file_name}"

# Configuration: check_same_thread=False for multithreading (FastAPI)
connect_args = {"check_same_thread": False}
# Create Engine
engine = create_engine(sqlite_url, connect_args=connect_args)

def create_db_and_tables():
    """
    创建数据库和表结构
    如果没有表会自动创建，有的话会跳过
    """
    SQLModel.metadata.create_all(engine)

def get_session():
    """
    获取数据库会话 (Dependency Injection)
    用于在每个请求中提供一个独立的数据库会话，请求结束自动关闭
    """
    with Session(engine) as session:
        yield session
