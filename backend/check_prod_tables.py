import sys
import os
from sqlmodel import create_engine, inspect

# Ensure we can import from backend
sys.path.append(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from backend.database import engine

def check_tables():
    print(f"Checking database at: {engine.url}")
    inspector = inspect(engine)
    tables = inspector.get_table_names()
    
    print("\nExisting Tables:")
    for table in tables:
        print(f" - {table}")
        
    required = ["uservote"]
    missing = [t for t in required if t not in tables]
    
    if missing:
        print(f"\n[CRITICAL] Missing tables: {missing}")
        print("Run 'python backend/create_tables.py' to fix this.")
    else:
        print("\n[OK] All required tables exist.")

if __name__ == "__main__":
    check_tables()
