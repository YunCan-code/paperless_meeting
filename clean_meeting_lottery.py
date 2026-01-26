
from sqlmodel import select, Session, delete
import sys
import os
import traceback

# Add project root to path
sys.path.append(os.getcwd())

try:
    from backend.database import engine
    from backend.models import Lottery, LotteryWinner
except ImportError:
    # If running inside /app where backend contents are in current dir or similar structure
    from database import engine
    from models import Lottery, LotteryWinner

def clean_meeting_lottery(meeting_id):
    """
    Clears all lottery data (Winners and Rounds) for the specified meeting_id.
    """
    print(f"!!! WARNING !!!")
    print(f"This will DELETE ALL Lottery history for Meeting ID: {meeting_id}")
    confirm = input("Type 'yes' to proceed: ")
    
    if confirm != 'yes':
        print("Operation cancelled.")
        return

    try:
        with Session(engine) as session:
            # 1. Find all lotteries for this meeting
            lotteries = session.exec(select(Lottery).where(Lottery.meeting_id == meeting_id)).all()
            lottery_ids = [lot.id for lot in lotteries]
            
            if not lottery_ids:
                print(f"No lottery records found for meeting {meeting_id}.")
                return

            print(f"Found {len(lotteries)} lottery rounds. IDs: {lottery_ids}")

            # 2. Delete Winners (Linked to these lotteries)
            # Efficient delete using IN clause logic or checking individual validity
            # SQLModel doesn't support bulk delete with join easily in one stmt sometimes, 
            # but we can delete by lottery_id
            
            winner_count = 0
            for lot_id in lottery_ids:
                stmt = delete(LotteryWinner).where(LotteryWinner.lottery_id == lot_id)
                result = session.exec(stmt)
                winner_count += result.rowcount
            
            print(f"Deleted {winner_count} winner records.")

            # 3. Delete Lotteries
            stmt_lot = delete(Lottery).where(Lottery.meeting_id == meeting_id)
            result_lot = session.exec(stmt_lot)
            print(f"Deleted {result_lot.rowcount} lottery round records.")

            session.commit()
            print("Successfully cleaned up lottery data.")
            
    except Exception as e:
        traceback.print_exc()
        print(f"Error: {e}")

if __name__ == "__main__":
    if len(sys.argv) > 1:
        mid = int(sys.argv[1])
    else:
        mid = 13 # Default for your current issue
        
    clean_meeting_lottery(mid)
