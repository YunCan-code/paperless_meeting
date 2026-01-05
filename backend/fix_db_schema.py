
import os
from sqlalchemy import create_engine, text

# Get DATABASE_URL from run.bat logic or just hardcode based on user's known state
# User is using: postgresql://postgres:123456@localhost:5432/paperless_meeting
DATABASE_URL = "postgresql://postgres:123456@localhost:5432/paperless_meeting"

engine = create_engine(DATABASE_URL)

def migrate():
    with engine.connect() as conn:
        print("Migrating database...")
        statements = [
            "ALTER TABLE device ADD COLUMN IF NOT EXISTS mac_address VARCHAR;",
            "ALTER TABLE device ADD COLUMN IF NOT EXISTS battery_level INTEGER;",
            "ALTER TABLE device ADD COLUMN IF NOT EXISTS is_charging BOOLEAN DEFAULT FALSE;",
            "ALTER TABLE device ADD COLUMN IF NOT EXISTS storage_total BIGINT;",
            "ALTER TABLE device ADD COLUMN IF NOT EXISTS storage_available BIGINT;"
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
