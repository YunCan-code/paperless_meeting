from sqlmodel import SQLModel, create_engine, Session

# 定义 SQLite 数据库文件名
sqlite_file_name = "database.db"
# 构造数据库连接 URL
sqlite_url = f"sqlite:///{sqlite_file_name}"

# 配置连接参数: check_same_thread=False 是为了让 SQLite 可以在多线程环境下使用 (FastAPI 是多线程的)
connect_args = {"check_same_thread": False}
# 创建数据库引擎
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
