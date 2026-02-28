from fastapi import APIRouter, Depends, HTTPException
from sqlmodel import Session, select
from sqlalchemy import and_
from datetime import datetime, timedelta
from typing import Optional
from pydantic import BaseModel

from database import get_session
from models import CheckIn, Meeting

router = APIRouter(prefix="/checkin", tags=["签到"])


class CheckInRequest(BaseModel):
    user_id: int
    meeting_id: int
    duration_minutes: Optional[int] = None


class MakeupRequest(BaseModel):
    user_id: int
    meeting_id: int
    duration_minutes: Optional[int] = None
    remark: Optional[str] = None


class CheckInResponse(BaseModel):
    id: int
    user_id: int
    meeting_id: int
    check_in_time: datetime
    duration_minutes: Optional[int]
    is_makeup: bool
    remark: Optional[str]
    meeting_title: Optional[str] = None


@router.post("/", response_model=CheckInResponse)
def check_in(req: CheckInRequest, session: Session = Depends(get_session)):
    """签到"""
    # 检查会议是否存在
    meeting = session.get(Meeting, req.meeting_id)
    if not meeting:
        raise HTTPException(status_code=404, detail="会议不存在")

    # 检查是否已签到
    existing = session.exec(
        select(CheckIn).where(
            and_(CheckIn.user_id == req.user_id, CheckIn.meeting_id == req.meeting_id)
        )
    ).first()
    if existing:
        raise HTTPException(status_code=400, detail="已签到，无需重复操作")

    checkin = CheckIn(
        user_id=req.user_id,
        meeting_id=req.meeting_id,
        duration_minutes=req.duration_minutes,
    )
    session.add(checkin)
    session.commit()
    session.refresh(checkin)
    return CheckInResponse(
        **checkin.dict(),
        meeting_title=meeting.title,
    )


@router.post("/makeup", response_model=CheckInResponse)
def makeup_check_in(req: MakeupRequest, session: Session = Depends(get_session)):
    """补签"""
    meeting = session.get(Meeting, req.meeting_id)
    if not meeting:
        raise HTTPException(status_code=404, detail="会议不存在")

    existing = session.exec(
        select(CheckIn).where(
            and_(CheckIn.user_id == req.user_id, CheckIn.meeting_id == req.meeting_id)
        )
    ).first()
    if existing:
        raise HTTPException(status_code=400, detail="已签到，无需补签")

    checkin = CheckIn(
        user_id=req.user_id,
        meeting_id=req.meeting_id,
        is_makeup=True,
        remark=req.remark,
        duration_minutes=req.duration_minutes,
    )
    session.add(checkin)
    session.commit()
    session.refresh(checkin)
    return CheckInResponse(
        **checkin.dict(),
        meeting_title=meeting.title,
    )


@router.delete("/{checkin_id}")
def cancel_check_in(checkin_id: int, session: Session = Depends(get_session)):
    """取消打卡"""
    checkin = session.get(CheckIn, checkin_id)
    if not checkin:
        raise HTTPException(status_code=404, detail="签到记录不存在")
    session.delete(checkin)
    session.commit()
    return {"message": "已取消打卡"}


@router.get("/today/{user_id}")
def get_today_status(user_id: int, session: Session = Depends(get_session)):
    """获取用户今日签到状态：今日哪些会议已签到、哪些未签到"""
    from zoneinfo import ZoneInfo
    cst = ZoneInfo("Asia/Shanghai")
    now_cst = datetime.now(cst)
    today_start = now_cst.replace(hour=0, minute=0, second=0, microsecond=0)
    today_end = today_start + timedelta(days=1)

    # 今日所有会议
    today_meetings = session.exec(
        select(Meeting).where(
            and_(Meeting.start_time >= today_start, Meeting.start_time < today_end)
        )
    ).all()

    # 用户今日已签到的会议ID
    checked_in = session.exec(
        select(CheckIn).where(
            and_(CheckIn.user_id == user_id,
                 CheckIn.check_in_time >= today_start,
                 CheckIn.check_in_time < today_end)
        )
    ).all()
    checked_meeting_ids = {c.meeting_id for c in checked_in}

    result = []
    for m in today_meetings:
        result.append({
            "meeting_id": m.id,
            "meeting_title": m.title,
            "start_time": m.start_time.isoformat(),
            "checked_in": m.id in checked_meeting_ids,
            "checkin_id": next((c.id for c in checked_in if c.meeting_id == m.id), None),
        })

    return {
        "today_meetings": result,
        "checked_count": len(checked_meeting_ids),
        "total_count": len(today_meetings),
    }
