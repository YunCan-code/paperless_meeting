import os
import sys

# Add current directory to sys.path so we can import from database
sys.path.append(os.path.dirname(os.path.abspath(__file__)))

from sqlalchemy import text
from database import engine

def fix_lottery_schema():
    print(f"Connecting to database using engine: {engine}")
    with engine.connect() as conn:
        print("Checking/Fixing lotterywinner schema...")
        try:
            # Check if column exists first to be safe
            # This query works for PostgreSQL
            check_sql = text("SELECT column_name FROM information_schema.columns WHERE table_name='lotterywinner' AND column_name='user_name';")
            result = conn.execute(check_sql).fetchone()
            
            if not result:
                print("Column 'user_name' not found in table 'lotterywinner'. Adding it...")
                # Add the column with a default value to handle existing rows
                conn.execute(text("ALTER TABLE lotterywinner ADD COLUMN user_name VARCHAR NOT NULL DEFAULT '';"))
                conn.commit()
                print("SUCCESS: Column 'user_name' added successfully.")
            else:
                print("INFO: Column 'user_name' already exists in table 'lotterywinner'. No action needed.")
                
        except Exception as e:
            print(f"ERROR: Failed to update schema. Details: {e}")
            import traceback
            traceback.print_exc()

if __name__ == "__main__":
    fix_lottery_schema()
