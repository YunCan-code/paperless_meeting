import os
from sqlalchemy import create_engine, text

# 获取环境变量中的数据库配置，自动适配 SQLite 或 PostgreSQL
DATABASE_URL = os.getenv("DATABASE_URL", None)

if DATABASE_URL is None:
    BASE_DIR = os.path.dirname(os.path.abspath(__file__))
    sqlite_file_name = os.path.join(BASE_DIR, "database.db")
    DATABASE_URL = f"sqlite:///{sqlite_file_name}"

engine = create_engine(DATABASE_URL)

def run_migration():
    print(f"[{DATABASE_URL}] 开始升级数据库结构...")
    with engine.begin() as conn:
        try:
            # 给现有 meeting 表添加 end_time 列
            # 注意: Sqlite 对于 IF NOT EXISTS 支持有限，如果使用的是 PostgreSQL 则完美支持
            # 如果是 Sqlite，捕获异常即可（sqlite 下添加重复列会报错_）
            if "sqlite" in DATABASE_URL:
                try:
                    conn.execute(text("ALTER TABLE meeting ADD COLUMN end_time DATETIME;"))
                    print("✅ SQLite: 成功为 meeting 表添加 end_time 列。")
                except Exception as e:
                    if "duplicate column name" in str(e).lower():
                        print("✅ SQLite: end_time 列已存在，无需添加。")
                    else:
                        print(f"❌ SQLite 变更失败: {e}")
            else:
                conn.execute(text("ALTER TABLE meeting ADD COLUMN IF NOT EXISTS end_time TIMESTAMP WITHOUT TIME ZONE;"))
                print("✅ PostgreSQL: 成功为 meeting 表添加 end_time 列。")
                
        except Exception as e:
            print(f"❌ 数据库升级遇到异常 (可能已存在或语法差异): {e}")

if __name__ == "__main__":
    run_migration()
