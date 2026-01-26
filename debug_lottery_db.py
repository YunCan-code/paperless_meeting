
from sqlmodel import select, Session
import sys
import os
import traceback

# Add project root to path
sys.path.append(os.getcwd())

from backend.database import engine
from backend.models import Lottery, LotteryWinner, User

def inspect_meeting(meeting_id):
    try:
        with open("debug_output.txt", "w", encoding="utf-8") as f:
            def log(msg):
                print(msg)
                f.write(str(msg) + "\n")

            with Session(engine) as session:
                log(f"--- Inspecting Meeting {meeting_id} ---")
                
                # 1. Get All Lotteries
                log("\n[Lottery Rounds]")
                lotteries = session.exec(select(Lottery).where(Lottery.meeting_id == meeting_id)).all()
                for lot in lotteries:
                    log(f"ID: {lot.id}, Title: {lot.title}, Status: {lot.status}, Created: {lot.created_at}")
                    
                # 2. Get All Winners
                log("\n[Winners]")
                stmt = select(LotteryWinner, User).join(User).join(Lottery).where(Lottery.meeting_id == meeting_id)
                results = session.exec(stmt).all()
                
                seen_users = set()
                for winner, user in results:
                    log(f"Winner: {user.name} (ID: {user.id}) in Lottery ID: {winner.lottery_id}")
                    seen_users.add(user.id)
                    
                log(f"\nUnique Winners in History: {seen_users}")
            
    except Exception as e:
        traceback.print_exc()
        print(f"Error: {e}")

if __name__ == "__main__":
    try:
        inspect_meeting(13)
    except Exception as e:
        print(f"Main Error: {e}")
