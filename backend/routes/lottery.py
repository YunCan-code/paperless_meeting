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
    获取今日所有有抽签活动(无论状态)的会议。
    """
    from datetime import date, datetime, time, timedelta

    # 1. 获取今日起始时间 (UTC+0? 还是本地时间? DB存储的是Naive或UTC)
    # 假设服务器本地时间运行正常，或者 DB 中 start_time 是 UTC。
    # 为了简单，查最近24小时? 或者今天0点。
    # 假设使用 UTC。Meeting.start_time 通常是 UTC。
    # TODO: 时区处理。这里简单使用 utcnow date。
    
    today = date.today()
    today_start = datetime.combine(today, time.min)
    
    # 查找:
    # 1. 状态为 active 或 pending 的 (无论时间)
    # 2. 状态为 finished 且是今天的
    from sqlalchemy import or_

    statement = select(Meeting).join(Lottery).where(
        or_(
            Lottery.status == "active",
            Lottery.status == "pending",
            (Lottery.status == "finished") & (Meeting.start_time >= today_start)
        )
    ).distinct()
    
    meetings = session.exec(statement).all()
    
    return meetings
