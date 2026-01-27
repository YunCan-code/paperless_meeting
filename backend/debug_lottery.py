import sys
import os

# Add current dir to path to allow imports
sys.path.append(os.getcwd())

try:
    from database import engine
    from models import Meeting, Lottery
except ImportError:
    # Try parent import if running from root
    sys.path.append(os.path.join(os.getcwd(), 'backend'))
    from backend.database import engine
    from backend.models import Meeting, Lottery

from sqlmodel import select, Session
from datetime import date, datetime, time
from sqlalchemy import or_

def check():
    print(f"CWD: {os.getcwd()}")
    try:
        with Session(engine) as session:
            # Check Today
            today = date.today()
            today_start = datetime.combine(today, time.min)
            print(f"Today Start (Local): {today_start}")

            # Check Lotteries
            lots = session.exec(select(Lottery)).all()
            print(f"Total Lotteries: {len(lots)}")
            
            for l in lots:
                m = session.get(Meeting, l.meeting_id)
                status = l.status
                m_start = m.start_time if m else None
                
                print(f"Lottery {l.id}: Status='{status}' Meeting={l.meeting_id} Start={m_start}")
                
            # Run Query
            print("-" * 20)
            print("Executing Active Query:")
            
            statement = select(Meeting).join(Lottery).where(
                or_(
                    Lottery.status == "active",
                    Lottery.status == "pending",
                    (Lottery.status == "finished") & (Meeting.start_time >= today_start)
                )
            ).distinct()
            
            try:
                results = session.exec(statement).all()
                print(f"Query Matched: {len(results)} meetings")
                for r in results:
                    print(f" - Meeting {r.id}: {r.title}")
            except Exception as e:
                print(f"Query Error: {e}")
                
    except Exception as e:
        print(f"Script Error: {e}")

if __name__ == "__main__":
    check()
