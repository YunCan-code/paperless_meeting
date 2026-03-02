import os
from sqlmodel import create_engine, text

DATABASE_URL = "postgresql://postgres:123456@localhost:5432/paperless_meeting"
engine = create_engine(DATABASE_URL)

def drop_role():
    with engine.begin() as conn:
        try:
            conn.execute(text('ALTER TABLE "user" DROP COLUMN IF EXISTS role;'))
            print("SQL execution complete: Dropped 'role' column from 'user' table.")
        except Exception as e:
            print(f"Exception during execution: {e}")

if __name__ == "__main__":
    drop_role()
