import psycopg2
import os

def migrate():
    db_url = os.environ.get("DATABASE_URL", "postgresql://postgres:postgres@localhost:5432/paperless_meeting")
    try:
        conn = psycopg2.connect(db_url)
        cur = conn.cursor()
        
        # Add winning_lottery_id column to lotteryparticipant
        print("Adding winning_lottery_id column to lotteryparticipant...")
        cur.execute("""
            ALTER TABLE lotteryparticipant 
            ADD COLUMN IF NOT EXISTS winning_lottery_id INTEGER;
        """)
        
        conn.commit()
        print("Migration successful!")
        cur.close()
        conn.close()
    except Exception as e:
        print(f"Migration failed: {e}")

if __name__ == "__main__":
    migrate()
