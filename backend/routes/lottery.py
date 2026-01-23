from fastapi import APIRouter, Depends
from sqlmodel import Session, select
from typing import List

from database import get_session
from models import Meeting, Lottery, MeetingRead, MeetingType, AttachmentRead, MeetingCardResponse

# 导入路由帮助函数（为了复用 MeetingCardResponse 图片逻辑）
# 由于 Python 循环导入限制，我们可能需要复制逻辑或重构。
# 为了简洁，简单复制 helper 逻辑，或只返回 MeetingRead 如果安卓端不需要封面逻辑。
# 安卓端 LotteryListScreen 使用 MeetingCardResponse 渲染吗？
# 查看 ui/screens/lottery/LotteryListScreen.kt: 显示 MeetingCard，看似只用了 title, startTime, id.
# 所以返回 MeetingRead 即可，不需要复杂的封面逻辑。

# 但为了兼容性，如果未来需要，我们可以返回 MeetingRead 扩展。
# 目前 MeetingCardResponse 继承自 MeetingRead。

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
