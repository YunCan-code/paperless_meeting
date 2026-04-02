import os
from sqlalchemy import inspect, text
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
        _ensure_compatible_meeting_schema()
        _ensure_compatible_device_schema()
        _ensure_compatible_vote_schema()
        _ensure_compatible_lottery_schema()
    except Exception as e:
        # 在多 worker 启动时，可能会遇到并发创建表的竞争条件
        # 如果甚至 "UniqueViolation" 等错误，通常意味着另一个 worker 已经创建了表
        print(f"[WARN] Database creation warning (likely race condition): {e}")

def _ensure_compatible_device_schema():
    """
    兼容旧库，补齐新增的设备版本号字段。
    """
    try:
        inspector = inspect(engine)
        if "device" not in inspector.get_table_names():
            return

        existing_columns = {column["name"] for column in inspector.get_columns("device")}
        if "app_version_code" in existing_columns:
            return

        with engine.begin() as connection:
            connection.execute(text("ALTER TABLE device ADD COLUMN app_version_code INTEGER"))

        print("[INFO] Added device.app_version_code column")
    except Exception as e:
        print(f"[WARN] Device schema compatibility check failed: {e}")

def _ensure_compatible_meeting_schema():
    """
    兼容旧库，补齐会议扩展字段。
    """
    try:
        inspector = inspect(engine)
        if "meeting" not in inspector.get_table_names():
            return

        existing_columns = {column["name"] for column in inspector.get_columns("meeting")}
        statements = []

        if "manual_attendees" not in existing_columns:
            if "sqlite" in DATABASE_URL:
                statements.append("ALTER TABLE meeting ADD COLUMN manual_attendees TEXT")
            else:
                statements.append("ALTER TABLE meeting ADD COLUMN manual_attendees TEXT")

        if "meeting_contacts" not in existing_columns:
            if "sqlite" in DATABASE_URL:
                statements.append("ALTER TABLE meeting ADD COLUMN meeting_contacts TEXT")
            else:
                statements.append("ALTER TABLE meeting ADD COLUMN meeting_contacts TEXT")

        if "show_media_link" not in existing_columns:
            if "sqlite" in DATABASE_URL:
                statements.append("ALTER TABLE meeting ADD COLUMN show_media_link BOOLEAN DEFAULT 0")
            else:
                statements.append("ALTER TABLE meeting ADD COLUMN show_media_link BOOLEAN DEFAULT FALSE")

        if "cover_image" not in existing_columns:
            if "sqlite" in DATABASE_URL:
                statements.append("ALTER TABLE meeting ADD COLUMN cover_image TEXT")
            else:
                statements.append("ALTER TABLE meeting ADD COLUMN cover_image TEXT")

        if "android_visibility_mode" not in existing_columns:
            if "sqlite" in DATABASE_URL:
                statements.append("ALTER TABLE meeting ADD COLUMN android_visibility_mode TEXT DEFAULT 'inherit'")
            else:
                statements.append("ALTER TABLE meeting ADD COLUMN android_visibility_mode VARCHAR DEFAULT 'inherit'")

        if "android_visibility_hide_after_hours" not in existing_columns:
            if "sqlite" in DATABASE_URL:
                statements.append("ALTER TABLE meeting ADD COLUMN android_visibility_hide_after_hours INTEGER")
            else:
                statements.append("ALTER TABLE meeting ADD COLUMN android_visibility_hide_after_hours INTEGER")

        if not statements:
            return

        with engine.begin() as connection:
            for statement in statements:
                connection.execute(text(statement))

        print("[INFO] Added meeting compatibility columns")
    except Exception as e:
        print(f"[WARN] Meeting schema compatibility check failed: {e}")

def _ensure_compatible_vote_schema():
    """
    兼容旧库，补齐投票扩展字段。
    """
    try:
        inspector = inspect(engine)
        if "vote" not in inspector.get_table_names():
            return

        existing_columns = {column["name"] for column in inspector.get_columns("vote")}
        statements = []

        if "countdown_seconds" not in existing_columns:
            if "sqlite" in DATABASE_URL:
                statements.append("ALTER TABLE vote ADD COLUMN countdown_seconds INTEGER DEFAULT 10")
            else:
                statements.append("ALTER TABLE vote ADD COLUMN countdown_seconds INTEGER DEFAULT 10")

        if "closed_at" not in existing_columns:
            if "sqlite" in DATABASE_URL:
                statements.append("ALTER TABLE vote ADD COLUMN closed_at TIMESTAMP")
            else:
                statements.append("ALTER TABLE vote ADD COLUMN closed_at TIMESTAMP")

        if not statements:
            return

        with engine.begin() as connection:
            for statement in statements:
                connection.execute(text(statement))

        print("[INFO] Added vote compatibility columns")
    except Exception as e:
        print(f"[WARN] Vote schema compatibility check failed: {e}")

def _ensure_compatible_lottery_schema():
    """
    兼容旧库中的抽签状态枚举值和轮次顺序字段。
    """
    try:
        inspector = inspect(engine)
        if "lottery" not in inspector.get_table_names():
            return

        existing_columns = {column["name"] for column in inspector.get_columns("lottery")}

        with engine.begin() as connection:
            if "sort_order" not in existing_columns:
                connection.execute(text("ALTER TABLE lottery ADD COLUMN sort_order INTEGER DEFAULT 0"))
                print("[INFO] Added lottery.sort_order column")

            connection.execute(
                text("UPDATE lottery SET status = 'draft' WHERE status IN ('pending', 'waiting', 'active')")
            )

            rows = connection.execute(
                text("SELECT id FROM lottery ORDER BY CASE WHEN sort_order IS NULL OR sort_order = 0 THEN 1 ELSE 0 END, sort_order, created_at, id")
            ).fetchall()
            for index, row in enumerate(rows, start=1):
                connection.execute(
                    text("UPDATE lottery SET sort_order = :sort_order WHERE id = :lottery_id"),
                    {"sort_order": index, "lottery_id": row[0]},
                )

        print("[INFO] Normalized lottery status values")
    except Exception as e:
        print(f"[WARN] Lottery schema compatibility check failed: {e}")

def get_session():
    """
    获取数据库会话 (Dependency Injection)
    用于在每个请求中提供一个独立的数据库会话，请求结束自动关闭
    """
    with Session(engine) as session:
        yield session
