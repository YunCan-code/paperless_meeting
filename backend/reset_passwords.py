import os
from sqlmodel import create_engine, Session, select
from models import User

DATABASE_URL = "postgresql://postgres:123456@localhost:5432/paperless_meeting"
engine = create_engine(DATABASE_URL)

def reset_passwords():
    with Session(engine) as session:
        users = session.exec(select(User)).all()
        updated_count = 0
        for user in users:
            if user.password and user.password.startswith("$2b$"):
                user.password = "123456" 
                updated_count += 1
        session.commit()
        print(f"SQL execution complete: Reset {updated_count} hashed passwords to plain text ('123456').")

if __name__ == "__main__":
    reset_passwords()
