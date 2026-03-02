import os
from sqlmodel import create_engine, text

DATABASE_URL = "postgresql://postgres:123456@localhost:5432/paperless_meeting"
engine = create_engine(DATABASE_URL)

with engine.begin() as conn:
    print("Latest meetings:")
    meetings = conn.execute(text("SELECT id, title FROM meeting ORDER BY id DESC LIMIT 5")).fetchall()
    for m in meetings:
        print(f"Meeting {m.id}: {m.title}")
        links = conn.execute(text(f"SELECT COUNT(*) FROM meetingattendeelink WHERE meeting_id={m.id}")).fetchone()
        print(f"  -> Attendees link count: {links[0]}")
