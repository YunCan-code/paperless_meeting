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
    from backend.models import Lottery, LotteryWinner, Meeting, User
except ImportError:
    from database import get_session
    from models import Lottery, LotteryWinner, Meeting, User

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
