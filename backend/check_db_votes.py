import sys
import os
sys.path.append(os.getcwd())
from backend.database import get_session
from backend.models import UserVote, User
from sqlmodel import select

session = next(get_session())
try:
    with open("db_check_result.txt", "w", encoding="utf-8") as f:
        f.write("Checking UserVote table...\n")
        votes = session.exec(select(UserVote)).all()
        f.write(f"Total UserVote records: {len(votes)}\n")
        for v in votes:
            f.write(f"User: {v.user_id}, Vote: {v.vote_id}, Option: {v.option_id}\n")
        
        f.write("\nChecking Users...\n")
        users = session.exec(select(User)).all()
        for u in users:
            f.write(f"User ID: {u.id}, Name: {u.name}\n")
        
except Exception as e:
    with open("db_check_result.txt", "w", encoding="utf-8") as f:
        f.write(f"Error: {e}")
finally:
    session.close()
