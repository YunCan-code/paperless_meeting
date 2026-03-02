import os
from sqlmodel import create_engine, text

DATABASE_URL = "postgresql://postgres:123456@localhost:5432/paperless_meeting"
engine = create_engine(DATABASE_URL)

def fix_db():
    with engine.begin() as conn:
        try:
            conn.execute(text("ALTER TABLE meeting ADD COLUMN end_time TIMESTAMP;"))
            print("SQL execution complete: Added 'end_time' column to 'meeting' table.")
        except Exception as e:
            print(f"Exception during execution: {e}")

if __name__ == "__main__":
    fix_db()
