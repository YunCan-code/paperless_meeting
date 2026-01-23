from fastapi import APIRouter, Depends
from sqlmodel import Session, select
from typing import List

from database import get_session
from models import Meeting, Lottery, MeetingRead

# Android端 LotteryListScreen 使用 MeetingRead 即可。
# 如果需要 MeetingCardResponse 的封面图逻辑，需要将其重构到 models.py 或 shared util。
# 目前为了修复启动报错，先简化为 List[MeetingRead]。

router = APIRouter(prefix="/lottery", tags=["lottery"])

@router.get("/active_meetings", response_model=List[MeetingRead])
def get_active_lottery_meetings(session: Session = Depends(get_session)):
    """
    获取当前有正在进行(active)或准备中(pending)抽签的会议。
    安卓端列表页调用此接口。
    """
    # 查找所有 status 为 'active' 或 'pending' 的 lottery 记录的 meeting_id
    statement = select(Lottery.meeting_id).where(
        (Lottery.status == "active") | (Lottery.status == "pending")
    ).distinct()
    
    meeting_ids = session.exec(statement).all()
    
    if not meeting_ids:
        return []

    # 获取会议详情
    # 同样只返回今天的会议? 或者全部?
    # 用户需求是"前端启动了抽签...才能看到"。如果不限日期，历史未完成的抽签也会显示。
    # 通常抽签是现场活动，所以加一个日期过滤是合理的，比如只显示最近24小时的，或者干脆只显示今天的。
    # 为了保险，加上 today 过滤（可选），但根据需求"前端启动了..."，
    # 如果前端启动了一个昨天的会议的抽签，应该也要显示。所以暂不加日期过滤，完全依赖 active 状态。
    
    meetings = session.exec(
        select(Meeting).where(Meeting.id.in_(meeting_ids))
    ).all()
    
    return meetings
