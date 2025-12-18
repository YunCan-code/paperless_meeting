from fastapi import APIRouter, Depends, HTTPException
from sqlmodel import Session, select
from typing import List
from database import get_session
from models import MeetingType, MeetingTypeRead

# 创建路由器，前缀为 /meeting_types
router = APIRouter(prefix="/meeting_types", tags=["meeting_types"])

@router.post("/", response_model=MeetingTypeRead)
def create_meeting_type(meeting_type: MeetingType, session: Session = Depends(get_session)):
    """
    创建新会议类型
    """
    session.add(meeting_type)
    session.commit()
    session.refresh(meeting_type)
    return meeting_type

@router.get("/", response_model=List[MeetingTypeRead])
def read_meeting_types(session: Session = Depends(get_session)):
    """
    获取所有会议类型
    """
    types = session.exec(select(MeetingType)).all()
    return types

@router.delete("/{type_id}")
def delete_meeting_type(type_id: int, session: Session = Depends(get_session)):
    """
    删除会议类型
    """
    meeting_type = session.get(MeetingType, type_id)
    if not meeting_type:
        raise HTTPException(status_code=404, detail="Meeting Type not found")
    session.delete(meeting_type)
    session.commit()
    return {"ok": True}

@router.put("/{type_id}", response_model=MeetingTypeRead)
def update_meeting_type(type_id: int, data: MeetingType, session: Session = Depends(get_session)):
    """
    更新会议类型
    """
    meeting_type = session.get(MeetingType, type_id)
    if not meeting_type:
        raise HTTPException(status_code=404, detail="Meeting Type not found")
    meeting_type.name = data.name
    meeting_type.description = data.description
    session.add(meeting_type)
    session.commit()
    session.refresh(meeting_type)
    return meeting_type
