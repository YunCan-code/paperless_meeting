"""
投票功能 HTTP API
用于 Web 管理端创建和管理投票
"""
from fastapi import APIRouter, Depends, HTTPException
from sqlmodel import Session, select
from sqlalchemy import func
from typing import List, Optional
from datetime import datetime, timedelta

from database import get_session
from models import Vote, VoteCreate, VoteRead, VoteOption, VoteOptionRead, VoteResult, VoteOptionResult, VoteOptionContent, VoteStatusUpdate, VoteSubmit, User, UserVote, MeetingAttendeeLink
from socket_manager import broadcast_vote_start, broadcast_vote_end, broadcast_vote_update

router = APIRouter(prefix="/vote", tags=["投票管理"])

@router.post("/{vote_id}/start")
async def start_vote(vote_id: int, session: Session = Depends(get_session)):
    """开始投票"""
    vote = session.get(Vote, vote_id)
    if not vote:
        raise HTTPException(status_code=404, detail="投票不存在")
    if vote.status != "draft":
        raise HTTPException(status_code=400, detail="只能启动草稿状态的投票")
    
    vote.status = "active"
    # 设置开始时间为当前时间 + 10秒 (倒计时缓冲)
    vote.started_at = datetime.now() + timedelta(seconds=10)
    session.add(vote)
    session.commit()
    
    # 广播 vote_start
    await broadcast_vote_start(vote.meeting_id, {
        "id": vote.id, 
        "title": vote.title,
        "duration_seconds": vote.duration_seconds,
        "started_at": vote.started_at.isoformat(),
        "wait_seconds": 10
    })
    return {"success": True, "vote_id": vote_id}

# ...

def _get_vote_with_options(vote_id: int, session: Session, include_remaining: bool = False) -> VoteRead:
    """辅助函数：获取带选项的投票"""
    vote = session.get(Vote, vote_id)
    if not vote:
        raise HTTPException(status_code=404, detail="投票不存在")
    
    options = session.exec(
        select(VoteOption).where(VoteOption.vote_id == vote_id).order_by(VoteOption.sort_order)
    ).all()
    
    remaining = None
    wait = None
    if include_remaining and vote.status == "active" and vote.started_at:
        now = datetime.now()
        # 计算是否需要等待 (倒计时)
        diff_start = (vote.started_at - now).total_seconds()
        
        if diff_start > 0:
            # 还在倒计时阶段
            wait = int(diff_start) + 1 # 向上取整
            remaining = vote.duration_seconds # 还没开始消耗时间
        else:
            # 已经开始
            wait = 0
            elapsed = (now - vote.started_at).total_seconds()
            remaining = max(0, vote.duration_seconds - int(elapsed))
    
    return VoteRead(
        id=vote.id,
        meeting_id=vote.meeting_id,
        title=vote.title,
        description=vote.description,
        is_multiple=vote.is_multiple,
        is_anonymous=vote.is_anonymous,
        max_selections=vote.max_selections,
        duration_seconds=vote.duration_seconds,
        status=vote.status,
        started_at=vote.started_at,
        created_at=vote.created_at,
        options=[VoteOptionRead(id=o.id, content=o.content, sort_order=o.sort_order) for o in options],
        remaining_seconds=remaining,
        wait_seconds=wait
    )





@router.post("/", response_model=VoteRead)
def create_vote(data: VoteCreate, session: Session = Depends(get_session)):
    """创建投票（Web管理端）"""
    vote = Vote(
        meeting_id=data.meeting_id,
        title=data.title,
        description=data.description,
        is_multiple=data.is_multiple,
        is_anonymous=data.is_anonymous,
        max_selections=data.max_selections,
        duration_seconds=data.duration_seconds,
        status="draft"
    )
    session.add(vote)
    session.commit()
    session.refresh(vote)
    
    # 添加选项
    for i, content in enumerate(data.options):
        option = VoteOption(vote_id=vote.id, content=content, sort_order=i)
        session.add(option)
    session.commit()
    
    return _get_vote_with_options(vote.id, session)


@router.get("/{vote_id}", response_model=VoteRead)
def get_vote(vote_id: int, user_id: Optional[int] = None, session: Session = Depends(get_session)):
    """获取投票详情"""
    return _get_vote_with_options(vote_id, session, include_remaining=True, user_id=user_id)


@router.post("/{vote_id}/start")
async def start_vote(vote_id: int, session: Session = Depends(get_session)):
    """开始投票"""
    vote = session.get(Vote, vote_id)
    if not vote:
        raise HTTPException(status_code=404, detail="投票不存在")
    if vote.status != "draft":
        raise HTTPException(status_code=400, detail="只能启动草稿状态的投票")
    
    vote.status = "active"
    vote.started_at = datetime.now()
    session.add(vote)
    session.commit()
    
    # 广播 vote_start
    await broadcast_vote_start(vote.meeting_id, {
        "id": vote.id, 
        "title": vote.title,
        "duration_seconds": vote.duration_seconds
    })
    return {"success": True, "vote_id": vote_id}


@router.post("/{vote_id}/close")
async def close_vote(vote_id: int, session: Session = Depends(get_session)):
    """结束投票"""
    vote = session.get(Vote, vote_id)
    if not vote:
        raise HTTPException(status_code=404, detail="投票不存在")
    
    vote.status = "closed"
    session.add(vote)
    session.commit()
    
    # 计算最终结果并广播
    final_results = _calculate_vote_result(vote_id, session)
    await broadcast_vote_end(vote.meeting_id, vote_id, final_results)
    
    return {"success": True, "vote_id": vote_id}


@router.get("/meeting/{meeting_id}/active", response_model=Optional[VoteRead])
def get_active_vote(meeting_id: int, user_id: Optional[int] = None, session: Session = Depends(get_session)):
    """获取会议当前进行中的投票"""
    stmt = select(Vote).where(Vote.meeting_id == meeting_id, Vote.status == "active")
    vote = session.exec(stmt).first()
    if not vote:
        return None
    return _get_vote_with_options(vote.id, session, include_remaining=True, user_id=user_id)


@router.post("/{vote_id}/submit")
async def submit_vote(vote_id: int, data: VoteSubmit, session: Session = Depends(get_session)):
    """提交投票（Android端）"""
    vote = session.get(Vote, vote_id)
    if not vote:
        raise HTTPException(status_code=404, detail="投票不存在")
    if vote.status != "active":
        raise HTTPException(status_code=400, detail="投票已结束或未开始")
    
    # 检查是否已投过
    existing = session.exec(
        select(UserVote).where(UserVote.vote_id == vote_id, UserVote.user_id == data.user_id)
    ).first()
    if existing:
        raise HTTPException(status_code=400, detail="您已投过票")
    
    # 验证选项数量
    if len(data.option_ids) > vote.max_selections:
        raise HTTPException(status_code=400, detail=f"最多只能选择{vote.max_selections}个选项")
    
    # 记录投票
    for option_id in data.option_ids:
        user_vote = UserVote(vote_id=vote_id, user_id=data.user_id, option_id=option_id)
        session.add(user_vote)
    session.commit()
    
    # 广播投票更新 (直接await确保执行)
    from socket_manager import broadcast_vote_update
    await broadcast_vote_update(
        meeting_id=vote.meeting_id,
        vote_id=vote_id,
        results=_calculate_vote_result(vote_id, session)['results']
    )
    
    return {"success": True}


from socket_manager import broadcast_vote_start, broadcast_vote_end

@router.get("/{vote_id}/result")
def get_vote_result(vote_id: int, session: Session = Depends(get_session)):
    """获取投票结果"""
    return _calculate_vote_result(vote_id, session)


def _calculate_vote_result(vote_id: int, session: Session):
    """辅助函数：计算投票结果"""
    vote = session.get(Vote, vote_id)
    if not vote:
        raise HTTPException(status_code=404, detail="投票不存在")
    
    # 获取选项
    options = session.exec(select(VoteOption).where(VoteOption.vote_id == vote_id)).all()
    
    # 统计各选项票数和总人数
    results = []
    
    # 总参与人数
    total_votes = session.exec(
        select(func.count(func.distinct(UserVote.user_id))).where(UserVote.vote_id == vote_id)
    ).one()
    
    for opt in options:
        # 该选项票数
        count = session.exec(
            select(func.count()).select_from(UserVote).where(UserVote.option_id == opt.id)
        ).one()
        
        percent = (count / total_votes * 100) if total_votes > 0 else 0
        
        # 获取投票人姓名 (如果非匿名)
        # 即使进行中也可以返回，前端根据状态决定是否显示，或者这里加逻辑控制
        # 用户要求：投票结束后显示。为灵活起见，后端都返回，前端控制。
        voters = []
        if not vote.is_anonymous:
            # Query User.name joined 
            stmt = select(User.name).join(UserVote, User.id == UserVote.user_id)\
                   .where(UserVote.option_id == opt.id)
            voters = session.exec(stmt).all()

        results.append({
            "option_id": opt.id,
            "content": opt.content,
            "count": count,
            "percent": round(percent, 1),
            "voters": voters
        })
    
    return {
        "vote_id": vote_id,
        "title": vote.title,
        "total_voters": total_votes,
        "results": results
    }


@router.get("/meeting/{meeting_id}/list", response_model=List[VoteRead])
def list_meeting_votes(meeting_id: int, user_id: Optional[int] = None, session: Session = Depends(get_session)):
    """获取会议所有投票"""
    votes = session.exec(select(Vote).where(Vote.meeting_id == meeting_id).order_by(Vote.created_at.desc())).all()
    return [_get_vote_with_options(v.id, session, include_remaining=True, user_id=user_id) for v in votes]


@router.get("/history", response_model=List[VoteRead])
def get_vote_history(
    user_id: int, 
    skip: int = 0, 
    limit: int = 20, 
    session: Session = Depends(get_session)
):
    """
    获取用户的投票历史
    逻辑：查询该用户实际投过票的记录
    """
    # 1. 查询用户实际参与投票的记录 (UserVote)
    stmt_voted = select(UserVote.vote_id).where(UserVote.user_id == user_id).distinct()
    voted_ids = session.exec(stmt_voted).all()
    
    if not voted_ids:
        return []

    # 2. 获取这些投票详情
    stmt_votes = select(Vote).where(
        Vote.id.in_(voted_ids)
    ).order_by(Vote.created_at.desc()).offset(skip).limit(limit)
    
    votes = session.exec(stmt_votes).all()
    
    return [_get_vote_with_options(v.id, session, include_remaining=False, user_id=user_id) for v in votes]


def _get_vote_with_options(vote_id: int, session: Session, include_remaining: bool = False, user_id: Optional[int] = None) -> VoteRead:
    """辅助函数：获取带选项的投票"""
    vote = session.get(Vote, vote_id)
    if not vote:
        raise HTTPException(status_code=404, detail="投票不存在")
    
    options = session.exec(
        select(VoteOption).where(VoteOption.vote_id == vote_id).order_by(VoteOption.sort_order)
    ).all()
    
    remaining = None
    if include_remaining and vote.status == "active" and vote.started_at:
        elapsed = (datetime.now() - vote.started_at).total_seconds()
        remaining = max(0, vote.duration_seconds - int(elapsed))
    
    # Check if user voted
    user_voted = False
    if user_id:
        existing = session.exec(
            select(UserVote).where(UserVote.vote_id == vote_id, UserVote.user_id == user_id)
        ).first()
        if existing:
            user_voted = True
    
    return VoteRead(
        id=vote.id,
        meeting_id=vote.meeting_id,
        title=vote.title,
        description=vote.description,
        is_multiple=vote.is_multiple,
        is_anonymous=vote.is_anonymous,
        max_selections=vote.max_selections,
        duration_seconds=vote.duration_seconds,
        status=vote.status,
        started_at=vote.started_at,
        created_at=vote.created_at,
        options=[VoteOptionRead(id=o.id, content=o.content, sort_order=o.sort_order) for o in options],
        remaining_seconds=remaining,
        user_voted=user_voted
    )
