from fastapi import APIRouter, Depends, HTTPException, Query
from sqlmodel import Session, select
from database import get_session
from models import MeetingSyncState
from typing import Optional
import time

router = APIRouter()

@router.post("/{meeting_id}/sync_state")
def update_sync_state(
    meeting_id: int, 
    file_id: int, 
    page_number: int, 
    is_syncing: bool = True,
    file_url: Optional[str] = None,
    session: Session = Depends(get_session)
):
    """
    主讲人调用：更新会议的当前同步状态
    """
    state = session.get(MeetingSyncState, meeting_id)
    if not state:
        state = MeetingSyncState(
            meeting_id=meeting_id,
            file_id=file_id,
            page_number=page_number,
            file_url=file_url,
            is_syncing=is_syncing,
            timestamp=time.time()
        )
        session.add(state)
    else:
        state.file_id = file_id
        state.page_number = page_number
        if file_url:
            state.file_url = file_url
        state.is_syncing = is_syncing
        state.timestamp = time.time()
        session.add(state)
    
    session.commit()
    session.refresh(state)
    return state

@router.get("/{meeting_id}/sync_state")
def get_sync_state(
    meeting_id: int,
    session: Session = Depends(get_session)
):
    """
    参会人轮询：获取当前同步状态
    """
    state = session.get(MeetingSyncState, meeting_id)
    if not state:
        # Return default idle state if no sync started yet
        return {
            "meeting_id": meeting_id,
            "is_syncing": False,
            "file_id": -1,
            "page_number": 0,
            "timestamp": 0
        }
    return state
