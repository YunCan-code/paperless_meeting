
import os
from sqlalchemy import create_engine, text

DATABASE_URL = "postgresql://postgres:123456@localhost:5432/paperless_meeting"

engine = create_engine(DATABASE_URL)

def migrate():
    with engine.connect() as conn:
        print("Migrating database (Alias)...")
        statements = [
            "ALTER TABLE device ADD COLUMN IF NOT EXISTS alias VARCHAR;"
        ]
        
        for stmt in statements:
            try:
                conn.execute(text(stmt))
                print(f"Executed: {stmt}")
            except Exception as e:
                print(f"Error executing {stmt}: {e}")
        
        conn.commit()
        print("Migration complete.")

if __name__ == "__main__":
    migrate()
