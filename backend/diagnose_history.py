import sys
import os
from sqlmodel import Session, select
from sqlalchemy import func

# Setup path
sys.path.append(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

try:
    from backend.database import engine
    from backend.models import Vote, UserVote, VoteOption
except ImportError:
    try:
        from database import engine
        from models import Vote, UserVote, VoteOption
    except ImportError:
        print("Import failed.")
        sys.exit(1)

def diagnose(user_id):
    print(f"--- Diagnosing History for User {user_id} ---")
    with Session(engine) as session:
        # 1. Check UserVote
        stmt_voted = select(UserVote.vote_id).where(UserVote.user_id == user_id).distinct()
        voted_ids = session.exec(stmt_voted).all()
        print(f"1. Valid Vote IDs in UserVote: {voted_ids}")
        print(f"   Count: {len(voted_ids)}")
        
        if not voted_ids:
            print("   -> No votes found for this user.")
            return

        # 2. Check Vote Records
        stmt_votes = select(Vote).where(
            Vote.id.in_(voted_ids)
        ).order_by(Vote.created_at.desc())
        
        votes = session.exec(stmt_votes).all()
        print(f"2. Vote Records Found: {len(votes)}")
        
        for v in votes:
            print(f"   - Vote [{v.id}] '{v.title}' Status: {v.status}")
            
            # 3. Check Options for each
            options = session.exec(
                select(VoteOption).where(VoteOption.vote_id == v.id)
            ).all()
            print(f"     Options count: {len(options)}")
            
            if not options:
                print("     [WARNING] No options found! This might cause frontend issues if not handled.")

if __name__ == "__main__":
    # Default to 48 (Wang Hongwen) or take arg
    target_id = 48
    if len(sys.argv) > 1:
        target_id = int(sys.argv[1])
    diagnose(target_id)
