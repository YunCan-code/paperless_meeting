
import os
from sqlalchemy import create_engine, text

DATABASE_URL = "postgresql://postgres:123456@localhost:5432/paperless_meeting"
engine = create_engine(DATABASE_URL)

def check_schema():
    with engine.connect() as conn:
        try:
            # Check columns in device table
            result = conn.execute(text("SELECT column_name FROM information_schema.columns WHERE table_name = 'device';"))
            columns = [row[0] for row in result]
            print(f"Device columns: {columns}")
            if 'alias' in columns:
                print("Column 'alias' EXISTS.")
            else:
                print("Column 'alias' MISSING.")
        except Exception as e:
            print(f"Error: {e}")

if __name__ == "__main__":
    check_schema()
