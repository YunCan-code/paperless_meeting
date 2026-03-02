import os
from sqlmodel import create_engine, text

# Replace with your actual database URL
DATABASE_URL = "postgresql://postgres:123456@localhost:5432/paperless_meeting"

engine = create_engine(DATABASE_URL)

def add_meeting_role_column():
    """Adds `meeting_role` column to `meetingattendeelink` table if it doesn't exist."""
    with engine.begin() as conn:
        try:
            # Check if column exists
            result = conn.execute(text(
                "SELECT column_name FROM information_schema.columns "
                "WHERE table_name='meetingattendeelink' AND column_name='meeting_role'"
            )).fetchone()

            if not result:
                # Add column
                conn.execute(text(
                    'ALTER TABLE "meetingattendeelink" ADD COLUMN "meeting_role" VARCHAR'
                ))
                print("Successfully added 'meeting_role' column to 'meetingattendeelink' table.")
            else:
                print("'meeting_role' column already exists in 'meetingattendeelink' table.")
        except Exception as e:
            print(f"Error adding column: {e}")

if __name__ == "__main__":
    add_meeting_role_column()
