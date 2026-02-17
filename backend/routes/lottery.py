"""
抽签功能 API 路由
"""
from fastapi import APIRouter, HTTPException, Depends
from sqlmodel import Session, select
from typing import List, Optional
from datetime import datetime
from pydantic import BaseModel

try:
    from backend.database import get_session
    from backend.models import Lottery, LotteryWinner, Meeting, User, MeetingAttendeeLink
except ImportError:
    from database import get_session
    from models import Lottery, LotteryWinner, Meeting, User, MeetingAttendeeLink

router = APIRouter(prefix="/lottery", tags=["lottery"])


# ========== 响应模型 ==========

class WinnerResponse(BaseModel):
    id: int
    user_name: str
    winning_at: datetime

class LotteryRoundResponse(BaseModel):
    id: int
    title: str
    count: int
    status: str
    winners: List[WinnerResponse]

class LotteryHistoryResponse(BaseModel):
    meeting_id: int
    meeting_title: str
    rounds: List[LotteryRoundResponse]


# ========== 请求模型 ==========

class LotteryCreateRequest(BaseModel):
    title: str
    count: int = 1
    allow_repeat: bool = False


# ========== API 端点 ==========

@router.get("/{meeting_id}/history", response_model=LotteryHistoryResponse)
def get_lottery_history(meeting_id: int, session: Session = Depends(get_session)):
    """获取会议的抽签历史"""
    meeting = session.get(Meeting, meeting_id)
    if not meeting:
        raise HTTPException(status_code=404, detail="会议不存在")
    
    stmt = select(Lottery).where(Lottery.meeting_id == meeting_id).order_by(Lottery.created_at)
    lotteries = session.exec(stmt).all()
    
    rounds = []
    for lottery in lotteries:
        winners = [
            WinnerResponse(id=w.id, user_name=w.user_name, winning_at=w.winning_at)
            for w in lottery.winners
        ]
        rounds.append(LotteryRoundResponse(
            id=lottery.id,
            title=lottery.title,
            count=lottery.count,
            status=lottery.status,
            winners=winners
        ))
    
    return LotteryHistoryResponse(
        meeting_id=meeting_id,
        meeting_title=meeting.title,
        rounds=rounds
    )


@router.get("/history/user/{user_id}", response_model=List[LotteryHistoryResponse])
def get_lottery_history_for_user(user_id: int, session: Session = Depends(get_session)):
    """获取用户参与的所有会议的抽签历史"""
    # 1. 查找用户参加的所有会议ID
    # 使用 MeetingAttendeeLink 表关联查询
    stmt_meetings = select(MeetingAttendeeLink.meeting_id).where(MeetingAttendeeLink.user_id == user_id)
    meeting_ids = session.exec(stmt_meetings).all()
    
    if not meeting_ids:
        return []

    # 2. 查找这些会议的所有抽签记录
    stmt_lotteries = select(Lottery).where(Lottery.meeting_id.in_(meeting_ids)).order_by(Lottery.created_at.desc())
    lotteries = session.exec(stmt_lotteries).all()
    
    # 3. 按会议分组组织数据
    meeting_lottery_map = {}
    
    for lottery in lotteries:
        if lottery.meeting_id not in meeting_lottery_map:
            meeting_lottery_map[lottery.meeting_id] = []
        meeting_lottery_map[lottery.meeting_id].append(lottery)
        
    result = []
    
    if not meeting_lottery_map:
        return []

    # 获取会议标题
    meetings = session.exec(select(Meeting).where(Meeting.id.in_(meeting_lottery_map.keys()))).all()
    meeting_title_map = {m.id: m.title for m in meetings}
    
    for meeting_id, meeting_lotteries in meeting_lottery_map.items():
        rounds = []
        for lottery in meeting_lotteries:
            winners = [
                WinnerResponse(id=w.id, user_name=w.user_name, winning_at=w.winning_at)
                for w in lottery.winners
            ]
            rounds.append(LotteryRoundResponse(
                id=lottery.id,
                title=lottery.title,
                count=lottery.count,
                status=lottery.status,
                winners=winners
            ))
            
        result.append(LotteryHistoryResponse(
            meeting_id=meeting_id,
            meeting_title=meeting_title_map.get(meeting_id, "未知会议"),
            rounds=rounds
        ))
        
    return result


@router.post("/{meeting_id}/round", response_model=LotteryRoundResponse)
def create_lottery_round(
    meeting_id: int, 
    request: LotteryCreateRequest,
    session: Session = Depends(get_session)
):
    """创建新的抽签轮次"""
    meeting = session.get(Meeting, meeting_id)
    if not meeting:
        raise HTTPException(status_code=404, detail="会议不存在")
    
    lottery = Lottery(
        meeting_id=meeting_id,
        title=request.title,
        count=request.count,
        allow_repeat=request.allow_repeat,
        status="pending"
    )
    session.add(lottery)
    session.commit()
    session.refresh(lottery)
    
    return LotteryRoundResponse(
        id=lottery.id,
        title=lottery.title,
        count=lottery.count,
        status=lottery.status,
        winners=[]
    )


@router.delete("/round/{lottery_id}")
def delete_lottery_round(lottery_id: int, session: Session = Depends(get_session)):
    """删除抽签轮次"""
    lottery = session.get(Lottery, lottery_id)
    if not lottery:
        raise HTTPException(status_code=404, detail="轮次不存在")
    
    # 删除关联的中奖记录
    for winner in lottery.winners:
        session.delete(winner)
    
    session.delete(lottery)
    session.commit()
    
    return {"message": "删除成功"}
