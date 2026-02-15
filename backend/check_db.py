
from sqlmodel import Session, create_engine, text
import os

DATABASE_URL = os.getenv("DATABASE_URL")
if not DATABASE_URL:
    print("DATABASE_URL not set")
    exit(1)

engine = create_engine(DATABASE_URL)
with Session(engine) as session:
    try:
        # Check counts and IDs
        result = session.execute(text("SELECT meeting_id, user_id, user_name, status FROM lotteryparticipant"))
        rows = result.all()
        print(f"Existing participants: {len(rows)}")
        for row in rows[:5]:
            print(f"  - Meeting: {row[0]}, ID: {row[1]}, Name: {row[2]}, Status: {row[3]}")
            
        # Check column names again just to be 100% sure for lotterywinner too
        result = session.execute(text("SELECT column_name FROM information_schema.columns WHERE table_name='lotterywinner'"))
        columns = [row[0] for row in result]
        print(f"Columns in lotterywinner: {columns}")
    except Exception as e:
        print(f"Error: {e}")
