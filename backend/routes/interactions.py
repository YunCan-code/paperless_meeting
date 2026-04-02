"""
会议互动中心快照接口
统一为后台互动中心与移动端会议详情提供投票/抽签概览。
"""
from typing import Optional

from fastapi import APIRouter, Depends, HTTPException
from sqlmodel import Session, select

from database import get_session
from models import Meeting, Vote
from services.lottery_service import build_session_snapshot
from routes.vote import _build_vote_read, _get_vote_or_404

router = APIRouter(prefix="/interactions", tags=["interactions"])


@router.get("/meeting/{meeting_id}/overview")
def get_meeting_interaction_overview(
    meeting_id: int,
    user_id: Optional[int] = None,
    session: Session = Depends(get_session),
):
    meeting = session.get(Meeting, meeting_id)
    if not meeting:
        raise HTTPException(status_code=404, detail="会议不存在")

    votes = session.exec(select(Vote).where(Vote.meeting_id == meeting_id).order_by(Vote.created_at.desc())).all()
    vote_items = [_build_vote_read(_get_vote_or_404(vote.id, session), session, user_id=user_id).model_dump() for vote in votes]
    active_vote = next((item for item in vote_items if item["status"] in {"countdown", "active"}), None)
    lottery_snapshot = build_session_snapshot(meeting_id, session, user_id=user_id)

    return {
        "meeting_id": meeting_id,
        "vote": {
            "active": active_vote,
            "items": vote_items,
            "draft_count": len([item for item in vote_items if item["status"] == "draft"]),
            "closed_count": len([item for item in vote_items if item["status"] == "closed"]),
        },
        "lottery": lottery_snapshot,
    }
