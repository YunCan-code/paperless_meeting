import os
from sqlmodel import Session, select, create_engine
from models import Meeting
from datetime import datetime, timezone, timedelta
from fastapi import APIRouter, Depends
from database import get_session

router = APIRouter(prefix="/debug", tags=["debug"])

@router.get("/week_buckets")
def debug_week_buckets(session: Session = Depends(get_session)):
    cst_tz = timezone(timedelta(hours=8))
    now_cst = datetime.now(cst_tz)
    
    # Calculate Buckets
    start_of_week = now_cst - timedelta(days=now_cst.weekday())
    start_of_week = start_of_week.replace(hour=0, minute=0, second=0, microsecond=0)
    end_of_week = start_of_week + timedelta(days=7)
    
    start_of_last = start_of_week - timedelta(days=7)
    end_of_last = start_of_week
    
    meetings = session.exec(select(Meeting)).all()
    
    result = {
        "now_cst": now_cst,
        "this_week_range": [start_of_week, end_of_week],
        "last_week_range": [start_of_last, end_of_last],
        "this_week_items": [],
        "last_week_items": [],
        "others": []
    }
    
    for m in meetings:
        # Convert DB (UTC) to CST
        dt_utc = m.start_time.replace(tzinfo=timezone.utc)
        dt_cst = dt_utc.astimezone(cst_tz)
        
        info = f"{m.id} - {m.title} - {dt_cst}"
        
        if start_of_week <= dt_cst < end_of_week:
            result["this_week_items"].append(info)
        elif start_of_last <= dt_cst < end_of_last:
            result["last_week_items"].append(info)
        else:
            result["others"].append(info)
            
    return result
