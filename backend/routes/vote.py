"""
投票功能 HTTP API
统一支持后台草稿配置、现场大屏控制与移动端参与。
"""
import logging
from datetime import datetime, timedelta, timezone
from typing import List, Optional

from fastapi import APIRouter, Depends, HTTPException
from sqlalchemy import func
from sqlalchemy.exc import IntegrityError
from sqlmodel import Session, delete, select

from database import get_session
from models import (
    MeetingAttendeeLink,
    User,
    UserVote,
    Vote,
    VoteCreate,
    VoteOption,
    VoteOptionRead,
    VoteRead,
    VoteResult,
    VoteSubmit,
    VoteUpdate,
)
from socket_manager import broadcast_vote_results, broadcast_vote_state

router = APIRouter(prefix="/vote", tags=["投票管理"])
logger = logging.getLogger(__name__)

UTC = timezone.utc
SHANGHAI_TZ = timezone(timedelta(hours=8), name="Asia/Shanghai")


def _local_now() -> datetime:
    return datetime.now(SHANGHAI_TZ).replace(tzinfo=None)


def _to_shanghai_naive(value: datetime, assume_tz: timezone) -> datetime:
    if value.tzinfo is None:
        return value.replace(tzinfo=assume_tz).astimezone(SHANGHAI_TZ).replace(tzinfo=None)
    return value.astimezone(SHANGHAI_TZ).replace(tzinfo=None)


def _resolve_runtime_datetime(value: Optional[datetime], vote: Vote) -> Optional[datetime]:
    if value is None:
        return None
    if value.tzinfo is not None:
        return value.astimezone(SHANGHAI_TZ).replace(tzinfo=None)
    if vote.status not in {"countdown", "active"}:
        return value

    now = _local_now()
    local_candidate = value
    utc_candidate = _to_shanghai_naive(value, UTC)
    upper_bound = vote.countdown_seconds if vote.status == "countdown" else vote.duration_seconds

    def _remaining_seconds(candidate: datetime) -> float:
        if vote.status == "countdown":
            return (candidate - now).total_seconds()
        return (candidate + timedelta(seconds=vote.duration_seconds) - now).total_seconds()

    local_remaining = _remaining_seconds(local_candidate)
    utc_remaining = _remaining_seconds(utc_candidate)
    local_valid = -5 <= local_remaining <= upper_bound + 60
    utc_valid = -5 <= utc_remaining <= upper_bound + 60

    if local_valid and not utc_valid:
        return local_candidate
    if utc_valid and not local_valid:
        return utc_candidate
    if local_valid and utc_valid:
        return local_candidate if abs(local_remaining) <= abs(utc_remaining) else utc_candidate

    return local_candidate if abs(local_remaining) <= abs(utc_remaining) else utc_candidate


def _normalize_vote_options(options: List[str]) -> List[str]:
    normalized = [str(option).strip() for option in options if str(option).strip()]
    if len(normalized) < 2:
        raise HTTPException(status_code=400, detail="至少需要 2 个有效选项")
    return normalized


def _validate_vote_payload(
    options: List[str],
    is_multiple: bool,
    max_selections: int,
    duration_seconds: int,
    countdown_seconds: int,
) -> None:
    if duration_seconds <= 0:
        raise HTTPException(status_code=400, detail="投票时长必须大于 0")
    if countdown_seconds < 0:
        raise HTTPException(status_code=400, detail="倒计时不能为负数")
    if is_multiple:
        if max_selections < 2:
            raise HTTPException(status_code=400, detail="多选投票至少允许选择 2 项")
        if max_selections > len(options):
            raise HTTPException(status_code=400, detail="最多选择数不能超过选项数量")
    else:
        if max_selections != 1:
            raise HTTPException(status_code=400, detail="单选投票最多只能选择 1 项")


def _sync_vote_runtime_state(vote: Vote, session: Session) -> Vote:
    now = _local_now()
    started_at = _resolve_runtime_datetime(vote.started_at, vote)

    if vote.status == "countdown" and started_at and now >= started_at:
        vote.status = "active"
        session.add(vote)
        session.commit()
        session.refresh(vote)
        started_at = _resolve_runtime_datetime(vote.started_at, vote)

    if vote.status == "active" and started_at:
        if now >= started_at + timedelta(seconds=vote.duration_seconds):
            vote.status = "closed"
            vote.closed_at = now
            session.add(vote)
            session.commit()
            session.refresh(vote)

    return vote


def _get_vote_or_404(vote_id: int, session: Session) -> Vote:
    vote = session.get(Vote, vote_id)
    if not vote:
        raise HTTPException(status_code=404, detail="投票不存在")
    return _sync_vote_runtime_state(vote, session)


def _get_vote_options(vote_id: int, session: Session) -> List[VoteOption]:
    return session.exec(
        select(VoteOption).where(VoteOption.vote_id == vote_id).order_by(VoteOption.sort_order, VoteOption.id)
    ).all()


def _get_total_voters(vote_id: int, session: Session) -> int:
    total = session.exec(
        select(func.count(func.distinct(UserVote.user_id))).where(UserVote.vote_id == vote_id)
    ).one()
    return int(total or 0)


def _get_user_voted(vote_id: int, user_id: Optional[int], session: Session) -> bool:
    if not user_id:
        return False
    return (
        session.exec(select(UserVote).where(UserVote.vote_id == vote_id, UserVote.user_id == user_id)).first()
        is not None
    )


def _ensure_user_can_join_vote(vote: Vote, user_id: int, session: Session) -> None:
    attendee = session.exec(
        select(MeetingAttendeeLink).where(
            MeetingAttendeeLink.meeting_id == vote.meeting_id,
            MeetingAttendeeLink.user_id == user_id,
        )
    ).first()
    if attendee is None:
        raise HTTPException(status_code=403, detail="您不在当前会议的参会名单中")


def _build_vote_result(vote: Vote, session: Session) -> VoteResult:
    vote = _sync_vote_runtime_state(vote, session)
    total_voters = _get_total_voters(vote.id, session)
    options = _get_vote_options(vote.id, session)

    results = []
    for option in options:
        count = session.exec(
            select(func.count()).select_from(UserVote).where(UserVote.option_id == option.id)
        ).one()
        count_value = int(count or 0)
        percent = round((count_value / total_voters * 100) if total_voters > 0 else 0, 1)

        voters: List[str] = []
        if vote.status == "closed" and not vote.is_anonymous:
            voters = session.exec(
                select(User.name)
                .join(UserVote, User.id == UserVote.user_id)
                .where(UserVote.option_id == option.id)
                .order_by(User.name)
            ).all()

        results.append(
            {
                "option_id": option.id,
                "content": option.content,
                "count": count_value,
                "percent": percent,
                "voters": voters,
            }
        )

    return VoteResult(
        vote_id=vote.id,
        title=vote.title,
        total_voters=total_voters,
        results=results,
    )


def _build_vote_read(vote: Vote, session: Session, user_id: Optional[int] = None) -> VoteRead:
    vote = _sync_vote_runtime_state(vote, session)
    options = _get_vote_options(vote.id, session)
    total_voters = _get_total_voters(vote.id, session)

    now = _local_now()
    started_at = _resolve_runtime_datetime(vote.started_at, vote)
    remaining_seconds: Optional[int] = None
    countdown_remaining_seconds: Optional[int] = None
    wait_seconds: Optional[int] = None

    if vote.status == "countdown" and started_at:
        countdown_remaining_seconds = max(0, int((started_at - now).total_seconds()))
        wait_seconds = countdown_remaining_seconds
    elif vote.status == "active" and started_at:
        remaining_seconds = max(0, int((started_at + timedelta(seconds=vote.duration_seconds) - now).total_seconds()))
        wait_seconds = 0

    return VoteRead(
        id=vote.id,
        meeting_id=vote.meeting_id,
        title=vote.title,
        description=vote.description,
        is_multiple=vote.is_multiple,
        is_anonymous=vote.is_anonymous,
        max_selections=vote.max_selections,
        duration_seconds=vote.duration_seconds,
        countdown_seconds=vote.countdown_seconds,
        status=vote.status,
        started_at=vote.started_at,
        closed_at=vote.closed_at,
        created_at=vote.created_at,
        options=[VoteOptionRead(id=o.id, content=o.content, sort_order=o.sort_order) for o in options],
        remaining_seconds=remaining_seconds,
        wait_seconds=wait_seconds,
        countdown_remaining_seconds=countdown_remaining_seconds,
        user_voted=_get_user_voted(vote.id, user_id, session),
        total_voters=total_voters,
    )


async def _broadcast_vote_snapshot(vote_id: int, session: Session) -> None:
    vote = _get_vote_or_404(vote_id, session)
    vote_read = _build_vote_read(vote, session)
    vote_result = _build_vote_result(vote, session)
    snapshot = vote_read.model_dump(mode="json")
    result_payload = vote_result.model_dump(mode="json")
    snapshot["results"] = result_payload["results"]
    await broadcast_vote_state(vote.meeting_id, snapshot)
    await broadcast_vote_results(vote.meeting_id, vote.id, result_payload)


async def _broadcast_vote_snapshot_safely(vote_id: int, session: Session) -> None:
    try:
        await _broadcast_vote_snapshot(vote_id, session)
    except Exception:
        logger.exception("投票广播失败，但数据库状态已提交: vote_id=%s", vote_id)


@router.post("/", response_model=VoteRead)
def create_vote(data: VoteCreate, session: Session = Depends(get_session)):
    options = _normalize_vote_options(data.options)
    _validate_vote_payload(
        options=options,
        is_multiple=data.is_multiple,
        max_selections=data.max_selections,
        duration_seconds=data.duration_seconds,
        countdown_seconds=data.countdown_seconds,
    )

    vote = Vote(
        meeting_id=data.meeting_id,
        title=data.title.strip(),
        description=(data.description or "").strip() or None,
        is_multiple=data.is_multiple,
        is_anonymous=data.is_anonymous,
        max_selections=data.max_selections,
        duration_seconds=data.duration_seconds,
        countdown_seconds=data.countdown_seconds,
        status="draft",
    )
    session.add(vote)
    session.commit()
    session.refresh(vote)

    for index, content in enumerate(options):
        session.add(VoteOption(vote_id=vote.id, content=content, sort_order=index))
    session.commit()
    session.refresh(vote)
    return _build_vote_read(vote, session)


@router.put("/{vote_id}", response_model=VoteRead)
def update_vote(vote_id: int, data: VoteUpdate, session: Session = Depends(get_session)):
    vote = _get_vote_or_404(vote_id, session)
    if vote.status != "draft":
        raise HTTPException(status_code=400, detail="只有草稿状态的投票允许编辑")

    payload = data.model_dump(exclude_unset=True)
    options = None
    if "options" in payload and payload["options"] is not None:
        options = _normalize_vote_options(payload["options"])

    is_multiple = payload.get("is_multiple", vote.is_multiple)
    max_selections = payload.get("max_selections", vote.max_selections)
    duration_seconds = payload.get("duration_seconds", vote.duration_seconds)
    countdown_seconds = payload.get("countdown_seconds", vote.countdown_seconds)
    option_list = options if options is not None else [option.content for option in _get_vote_options(vote.id, session)]

    _validate_vote_payload(option_list, is_multiple, max_selections, duration_seconds, countdown_seconds)

    for field in (
        "title",
        "description",
        "is_multiple",
        "is_anonymous",
        "max_selections",
        "duration_seconds",
        "countdown_seconds",
    ):
        if field in payload:
            value = payload[field]
            if field in {"title", "description"} and value is not None:
                value = value.strip() or None
            setattr(vote, field, value)

    session.add(vote)
    session.commit()

    if options is not None:
        session.exec(delete(VoteOption).where(VoteOption.vote_id == vote.id))
        session.commit()
        for index, content in enumerate(options):
            session.add(VoteOption(vote_id=vote.id, content=content, sort_order=index))
        session.commit()

    session.refresh(vote)
    return _build_vote_read(vote, session)


@router.delete("/{vote_id}")
def delete_vote(vote_id: int, session: Session = Depends(get_session)):
    vote = _get_vote_or_404(vote_id, session)
    if vote.status != "draft":
        raise HTTPException(status_code=400, detail="只有草稿状态的投票允许删除")

    session.exec(delete(UserVote).where(UserVote.vote_id == vote.id))
    session.exec(delete(VoteOption).where(VoteOption.vote_id == vote.id))
    session.delete(vote)
    session.commit()
    return {"ok": True}


@router.get("/meeting/{meeting_id}/list", response_model=List[VoteRead])
def list_meeting_votes(meeting_id: int, user_id: Optional[int] = None, session: Session = Depends(get_session)):
    votes = session.exec(select(Vote).where(Vote.meeting_id == meeting_id).order_by(Vote.created_at.desc())).all()
    return [_build_vote_read(vote, session, user_id=user_id) for vote in votes]


@router.get("/meeting/{meeting_id}/active", response_model=Optional[VoteRead])
def get_active_vote(meeting_id: int, user_id: Optional[int] = None, session: Session = Depends(get_session)):
    votes = session.exec(
        select(Vote)
        .where(Vote.meeting_id == meeting_id, Vote.status.in_(["countdown", "active"]))
        .order_by(Vote.created_at.desc())
    ).all()
    for vote in votes:
        vote = _sync_vote_runtime_state(vote, session)
        if vote.status in {"countdown", "active"}:
            return _build_vote_read(vote, session, user_id=user_id)
    return None


@router.get("/history", response_model=List[VoteRead])
def get_vote_history(user_id: int, skip: int = 0, limit: int = 20, session: Session = Depends(get_session)):
    vote_ids = session.exec(
        select(UserVote.vote_id).where(UserVote.user_id == user_id).distinct().offset(skip).limit(limit)
    ).all()
    if not vote_ids:
        return []

    votes = session.exec(select(Vote).where(Vote.id.in_(vote_ids)).order_by(Vote.created_at.desc())).all()
    return [_build_vote_read(vote, session, user_id=user_id) for vote in votes]


@router.get("/{vote_id}", response_model=VoteRead)
def get_vote(vote_id: int, user_id: Optional[int] = None, session: Session = Depends(get_session)):
    vote = _get_vote_or_404(vote_id, session)
    return _build_vote_read(vote, session, user_id=user_id)


@router.post("/{vote_id}/start")
async def start_vote(vote_id: int, session: Session = Depends(get_session)):
    vote = _get_vote_or_404(vote_id, session)
    if vote.status != "draft":
        raise HTTPException(status_code=400, detail="只能启动草稿状态的投票")

    vote.status = "countdown"
    vote.started_at = _local_now() + timedelta(seconds=vote.countdown_seconds)
    vote.closed_at = None
    session.add(vote)
    session.commit()
    session.refresh(vote)

    await _broadcast_vote_snapshot_safely(vote.id, session)
    return {"success": True, "vote_id": vote.id}


@router.post("/{vote_id}/close")
async def close_vote(vote_id: int, session: Session = Depends(get_session)):
    vote = _get_vote_or_404(vote_id, session)
    if vote.status not in {"countdown", "active"}:
        raise HTTPException(status_code=400, detail="当前投票不在进行中")

    vote.status = "closed"
    vote.closed_at = _local_now()
    session.add(vote)
    session.commit()
    session.refresh(vote)

    await _broadcast_vote_snapshot_safely(vote.id, session)
    return {"success": True, "vote_id": vote.id}


@router.post("/{vote_id}/submit")
async def submit_vote(vote_id: int, data: VoteSubmit, session: Session = Depends(get_session)):
    vote = _get_vote_or_404(vote_id, session)
    if vote.status != "active":
        raise HTTPException(status_code=400, detail="投票未开始或已结束")
    _ensure_user_can_join_vote(vote, data.user_id, session)

    if _get_user_voted(vote.id, data.user_id, session):
        raise HTTPException(status_code=400, detail="您已投过票")

    option_ids = list(dict.fromkeys(data.option_ids))
    if not option_ids:
        raise HTTPException(status_code=400, detail="请至少选择 1 个选项")
    if len(option_ids) > vote.max_selections:
        raise HTTPException(status_code=400, detail=f"最多只能选择 {vote.max_selections} 个选项")
    if not vote.is_multiple and len(option_ids) != 1:
        raise HTTPException(status_code=400, detail="单选投票只能选择 1 个选项")

    valid_option_ids = {option.id for option in _get_vote_options(vote.id, session)}
    if any(option_id not in valid_option_ids for option_id in option_ids):
        raise HTTPException(status_code=400, detail="存在无效的投票选项")

    for option_id in option_ids:
        session.add(UserVote(vote_id=vote.id, user_id=data.user_id, option_id=option_id))

    try:
        session.commit()
    except IntegrityError:
        session.rollback()
        raise HTTPException(status_code=400, detail="您已投过票")

    await _broadcast_vote_snapshot_safely(vote.id, session)
    return {"success": True}


@router.get("/{vote_id}/result", response_model=VoteResult)
def get_vote_result(vote_id: int, session: Session = Depends(get_session)):
    vote = _get_vote_or_404(vote_id, session)
    return _build_vote_result(vote, session)
