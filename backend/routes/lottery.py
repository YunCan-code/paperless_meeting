"""
抽签功能 API
数据库会话为真相源，Socket 仅广播状态快照。
"""
import random
from typing import Optional

from fastapi import APIRouter, Depends, HTTPException
from pydantic import BaseModel
from sqlmodel import Session, select

from database import get_session
from models import Lottery, LotteryParticipant, LotteryWinner, User
from services.lottery_service import (
    LOTTERY_ROUND_DRAFT,
    LOTTERY_ROUND_FINISHED,
    LOTTERY_ROUND_READY,
    LOTTERY_SESSION_COLLECTING,
    LOTTERY_SESSION_COMPLETED,
    LOTTERY_SESSION_IDLE,
    LOTTERY_SESSION_READY,
    LOTTERY_SESSION_RESULT,
    LOTTERY_SESSION_ROLLING,
    build_round_payload,
    build_session_snapshot,
    ensure_lottery_session,
    ensure_meeting_or_404,
    ensure_round_or_404,
    get_joined_participants,
    get_rounds,
    is_self_service_locked,
    lottery_now,
    normalize_round_orders,
    recalculate_participant_winner_flags,
    resolve_round_for_roll,
    resolve_session_status,
    round_status,
    serialize_winners,
)
from socket_manager import broadcast_lottery_session_change

router = APIRouter(prefix="/lottery", tags=["lottery"])


class LotteryCreateRequest(BaseModel):
    title: str
    count: int = 1
    allow_repeat: bool = False


class LotteryRoundUpdateRequest(BaseModel):
    title: Optional[str] = None
    count: Optional[int] = None
    allow_repeat: Optional[bool] = None


class LotteryParticipantActionRequest(BaseModel):
    user_id: int


class LotteryPrepareRequest(BaseModel):
    lottery_id: int


class LotteryMoveRequest(BaseModel):
    direction: str


async def _broadcast_snapshot(meeting_id: int, session: Session) -> dict:
    snapshot = build_session_snapshot(meeting_id, session)
    await broadcast_lottery_session_change(meeting_id, snapshot)
    return snapshot


def _sync_session_after_participant_change(
    meeting_id: int,
    lottery_session,
    session: Session,
) -> None:
    current_round = session.get(Lottery, lottery_session.current_round_id) if lottery_session.current_round_id else None
    current_round_finished = bool(current_round and round_status(current_round) == LOTTERY_ROUND_FINISHED)
    locked = is_self_service_locked(lottery_session, get_rounds(meeting_id, session))

    if lottery_session.session_status == LOTTERY_SESSION_ROLLING:
        pass
    elif locked and lottery_session.session_status in {LOTTERY_SESSION_RESULT, LOTTERY_SESSION_COMPLETED}:
        pass
    else:
        lottery_session.session_status = resolve_session_status(
            meeting_id,
            session,
            has_current_round=bool(lottery_session.current_round_id and not current_round_finished),
        )

    lottery_session.updated_at = lottery_now()
    session.add(lottery_session)


def _upsert_participant(
    meeting_id: int,
    user_id: int,
    session: Session,
) -> LotteryParticipant:
    user = session.get(User, user_id)
    if not user:
        raise HTTPException(status_code=404, detail="用户不存在")

    participant = session.get(LotteryParticipant, (meeting_id, user_id))
    if not participant:
        participant = LotteryParticipant(
            meeting_id=meeting_id,
            user_id=user.id,
            user_name=user.name,
            avatar=None,
            department=user.department,
            status="joined",
            is_winner=False,
            winning_lottery_id=None,
        )
    else:
        participant.status = "joined"
        participant.user_name = user.name
        participant.department = user.department
    session.add(participant)
    return participant


def _mark_participant_left(
    meeting_id: int,
    user_id: int,
    session: Session,
) -> LotteryParticipant:
    participant = session.get(LotteryParticipant, (meeting_id, user_id))
    if not participant:
        raise HTTPException(status_code=404, detail="参与者不存在")
    participant.status = "left"
    session.add(participant)
    return participant


@router.get("/{meeting_id}/history")
def get_lottery_history(meeting_id: int, session: Session = Depends(get_session)):
    meeting = ensure_meeting_or_404(meeting_id, session)
    rounds = get_rounds(meeting_id, session)
    round_order_map = {round_item.id: index for index, round_item in enumerate(rounds, start=1)}
    return {
        "meeting_id": meeting_id,
        "meeting_title": meeting.title,
        "rounds": [build_round_payload(round_item, round_order_map.get(round_item.id)) for round_item in rounds],
    }


@router.get("/{meeting_id}/rounds")
def get_lottery_rounds(meeting_id: int, session: Session = Depends(get_session)):
    ensure_meeting_or_404(meeting_id, session)
    rounds = get_rounds(meeting_id, session)
    round_order_map = {round_item.id: index for index, round_item in enumerate(rounds, start=1)}
    return {"items": [build_round_payload(round_item, round_order_map.get(round_item.id)) for round_item in rounds]}


@router.get("/{meeting_id}/session")
def get_lottery_session(meeting_id: int, user_id: Optional[int] = None, session: Session = Depends(get_session)):
    return build_session_snapshot(meeting_id, session, user_id=user_id)


@router.post("/{meeting_id}/round")
async def create_lottery_round(
    meeting_id: int,
    request: LotteryCreateRequest,
    session: Session = Depends(get_session),
):
    ensure_meeting_or_404(meeting_id, session)
    if not request.title.strip():
        raise HTTPException(status_code=400, detail="轮次名称不能为空")
    if request.count <= 0:
        raise HTTPException(status_code=400, detail="中奖人数必须大于 0")

    existing_rounds = get_rounds(meeting_id, session)
    if normalize_round_orders(existing_rounds, session):
        session.commit()
        existing_rounds = get_rounds(meeting_id, session)
    next_sort_order = max((round_item.sort_order for round_item in existing_rounds if isinstance(round_item.sort_order, int) and round_item.sort_order > 0), default=0) + 1

    round_item = Lottery(
        meeting_id=meeting_id,
        title=request.title.strip(),
        count=request.count,
        allow_repeat=request.allow_repeat,
        sort_order=next_sort_order,
        status=LOTTERY_ROUND_DRAFT,
    )
    session.add(round_item)
    session.commit()
    session.refresh(round_item)
    snapshot = build_round_payload(round_item)
    await _broadcast_snapshot(meeting_id, session)
    return snapshot


@router.put("/round/{lottery_id}")
async def update_lottery_round(
    lottery_id: int,
    request: LotteryRoundUpdateRequest,
    session: Session = Depends(get_session),
):
    round_item = ensure_round_or_404(lottery_id, session)
    if round_status(round_item) == LOTTERY_ROUND_FINISHED:
        raise HTTPException(status_code=400, detail="已完成轮次不允许编辑")

    payload = request.model_dump(exclude_unset=True)
    if "title" in payload:
        title = (payload["title"] or "").strip()
        if not title:
            raise HTTPException(status_code=400, detail="轮次名称不能为空")
        round_item.title = title
    if "count" in payload:
        if payload["count"] <= 0:
            raise HTTPException(status_code=400, detail="中奖人数必须大于 0")
        round_item.count = payload["count"]
    if "allow_repeat" in payload:
        round_item.allow_repeat = payload["allow_repeat"]

    session.add(round_item)
    session.commit()
    session.refresh(round_item)
    snapshot = build_round_payload(round_item)
    await _broadcast_snapshot(round_item.meeting_id, session)
    return snapshot


@router.delete("/round/{lottery_id}")
async def delete_lottery_round(lottery_id: int, session: Session = Depends(get_session)):
    round_item = ensure_round_or_404(lottery_id, session)
    meeting_id = round_item.meeting_id
    lottery_session = ensure_lottery_session(meeting_id, session)

    winners = session.exec(select(LotteryWinner).where(LotteryWinner.lottery_id == round_item.id)).all()
    for winner in winners:
        session.delete(winner)

    participants = session.exec(
        select(LotteryParticipant).where(
            LotteryParticipant.meeting_id == meeting_id,
            LotteryParticipant.winning_lottery_id == round_item.id,
        )
    ).all()
    for participant in participants:
        participant.winning_lottery_id = None
        session.add(participant)

    if lottery_session.current_round_id == round_item.id:
        lottery_session.current_round_id = None
        lottery_session.session_status = resolve_session_status(meeting_id, session, has_current_round=False)
        lottery_session.last_result = None
        lottery_session.updated_at = lottery_now()
        session.add(lottery_session)

    session.delete(round_item)
    session.commit()
    remaining_rounds = session.exec(select(Lottery).where(Lottery.meeting_id == meeting_id)).all()
    dirty = False
    if normalize_round_orders(remaining_rounds, session):
        dirty = True
    if recalculate_participant_winner_flags(meeting_id, session, rounds=remaining_rounds):
        dirty = True
    if dirty:
        session.commit()
    await _broadcast_snapshot(meeting_id, session)
    return {"ok": True}


@router.post("/{meeting_id}/participants/join")
async def join_lottery_pool(
    meeting_id: int,
    request: LotteryParticipantActionRequest,
    session: Session = Depends(get_session),
):
    ensure_meeting_or_404(meeting_id, session)
    lottery_session = ensure_lottery_session(meeting_id, session)
    if is_self_service_locked(lottery_session, get_rounds(meeting_id, session)):
        raise HTTPException(status_code=400, detail="抽签已开始，当前不能加入抽签池")

    _upsert_participant(meeting_id, request.user_id, session)
    _sync_session_after_participant_change(meeting_id, lottery_session, session)
    session.commit()

    return await _broadcast_snapshot(meeting_id, session)


@router.post("/{meeting_id}/participants/quit")
async def quit_lottery_pool(
    meeting_id: int,
    request: LotteryParticipantActionRequest,
    session: Session = Depends(get_session),
):
    ensure_meeting_or_404(meeting_id, session)
    lottery_session = ensure_lottery_session(meeting_id, session)
    if is_self_service_locked(lottery_session, get_rounds(meeting_id, session)):
        raise HTTPException(status_code=400, detail="抽签已开始，当前不能退出抽签池")

    _mark_participant_left(meeting_id, request.user_id, session)
    _sync_session_after_participant_change(meeting_id, lottery_session, session)
    session.commit()

    return await _broadcast_snapshot(meeting_id, session)


@router.post("/{meeting_id}/participants/admin/add")
async def admin_add_lottery_participant(
    meeting_id: int,
    request: LotteryParticipantActionRequest,
    session: Session = Depends(get_session),
):
    ensure_meeting_or_404(meeting_id, session)
    lottery_session = ensure_lottery_session(meeting_id, session)

    _upsert_participant(meeting_id, request.user_id, session)
    _sync_session_after_participant_change(meeting_id, lottery_session, session)
    session.commit()

    return await _broadcast_snapshot(meeting_id, session)


@router.post("/{meeting_id}/participants/admin/remove")
async def admin_remove_lottery_participant(
    meeting_id: int,
    request: LotteryParticipantActionRequest,
    session: Session = Depends(get_session),
):
    ensure_meeting_or_404(meeting_id, session)
    lottery_session = ensure_lottery_session(meeting_id, session)

    _mark_participant_left(meeting_id, request.user_id, session)
    _sync_session_after_participant_change(meeting_id, lottery_session, session)
    session.commit()

    return await _broadcast_snapshot(meeting_id, session)


@router.post("/{meeting_id}/prepare")
async def prepare_lottery_round(
    meeting_id: int,
    request: LotteryPrepareRequest,
    session: Session = Depends(get_session),
):
    ensure_meeting_or_404(meeting_id, session)
    lottery_session = ensure_lottery_session(meeting_id, session)
    round_item = ensure_round_or_404(request.lottery_id, session)
    if round_item.meeting_id != meeting_id:
        raise HTTPException(status_code=400, detail="轮次与会议不匹配")

    if round_status(round_item) == LOTTERY_ROUND_FINISHED:
        raise HTTPException(status_code=400, detail="该轮次已完成")

    for candidate in get_rounds(meeting_id, session):
        if candidate.id != round_item.id and round_status(candidate) == LOTTERY_ROUND_READY:
            candidate.status = LOTTERY_ROUND_DRAFT
            session.add(candidate)

    round_item.status = LOTTERY_ROUND_READY
    lottery_session.current_round_id = round_item.id
    lottery_session.session_status = resolve_session_status(meeting_id, session, has_current_round=True)
    lottery_session.last_result = None
    lottery_session.updated_at = lottery_now()

    session.add(round_item)
    session.add(lottery_session)
    session.commit()

    return await _broadcast_snapshot(meeting_id, session)


@router.post("/round/{lottery_id}/move")
async def move_lottery_round(
    lottery_id: int,
    request: LotteryMoveRequest,
    session: Session = Depends(get_session),
):
    round_item = ensure_round_or_404(lottery_id, session)
    lottery_session = ensure_lottery_session(round_item.meeting_id, session)

    if lottery_session.session_status == LOTTERY_SESSION_ROLLING:
        raise HTTPException(status_code=400, detail="抽签进行中，暂时不能调整顺序")
    if round_status(round_item) == LOTTERY_ROUND_FINISHED:
        raise HTTPException(status_code=400, detail="已完成轮次不允许调整顺序")
    if lottery_session.current_round_id == round_item.id:
        raise HTTPException(status_code=400, detail="当前轮次不允许调整顺序")
    if request.direction not in {"up", "down"}:
        raise HTTPException(status_code=400, detail="移动方向无效")

    rounds = get_rounds(round_item.meeting_id, session)
    if normalize_round_orders(rounds, session):
        session.commit()
        rounds = get_rounds(round_item.meeting_id, session)
    current_index = next((index for index, item in enumerate(rounds) if item.id == round_item.id), None)
    if current_index is None:
        raise HTTPException(status_code=404, detail="轮次不存在")

    target_index = current_index - 1 if request.direction == "up" else current_index + 1
    if target_index < 0 or target_index >= len(rounds):
        raise HTTPException(status_code=400, detail="当前轮次已无法继续移动")

    target_round = rounds[target_index]
    if round_status(target_round) == LOTTERY_ROUND_FINISHED:
        raise HTTPException(status_code=400, detail="不能跨过已完成轮次调整顺序")
    if lottery_session.current_round_id == target_round.id:
        raise HTTPException(status_code=400, detail="不能跨过当前轮次调整顺序")

    round_item.sort_order, target_round.sort_order = target_round.sort_order, round_item.sort_order
    session.add(round_item)
    session.add(target_round)
    session.commit()

    return await _broadcast_snapshot(round_item.meeting_id, session)


@router.post("/{meeting_id}/roll")
async def start_lottery_roll(meeting_id: int, session: Session = Depends(get_session)):
    ensure_meeting_or_404(meeting_id, session)
    lottery_session = ensure_lottery_session(meeting_id, session)
    rounds = get_rounds(meeting_id, session)
    round_item = resolve_round_for_roll(lottery_session, rounds)
    if not round_item:
        raise HTTPException(status_code=400, detail="当前没有可开始的轮次")

    participants = get_joined_participants(meeting_id, session)
    if not participants:
        raise HTTPException(status_code=400, detail="当前没有可参与抽签的人员")
    if lottery_session.session_status not in {LOTTERY_SESSION_COLLECTING, LOTTERY_SESSION_READY, LOTTERY_SESSION_RESULT, LOTTERY_SESSION_COMPLETED}:
        raise HTTPException(status_code=400, detail="当前状态无法开始抽签")

    lottery_session.current_round_id = round_item.id
    lottery_session.session_status = LOTTERY_SESSION_ROLLING
    lottery_session.self_service_locked = True
    lottery_session.updated_at = lottery_now()
    round_item.status = LOTTERY_ROUND_READY
    session.add(round_item)
    session.add(lottery_session)
    session.commit()

    return await _broadcast_snapshot(meeting_id, session)


@router.post("/{meeting_id}/stop")
async def stop_lottery_roll(meeting_id: int, session: Session = Depends(get_session)):
    ensure_meeting_or_404(meeting_id, session)
    lottery_session = ensure_lottery_session(meeting_id, session)
    if lottery_session.session_status != LOTTERY_SESSION_ROLLING:
        raise HTTPException(status_code=400, detail="抽签未在进行中")
    if not lottery_session.current_round_id:
        raise HTTPException(status_code=400, detail="当前没有准备好的轮次")

    round_item = ensure_round_or_404(lottery_session.current_round_id, session)
    participants = get_joined_participants(meeting_id, session)
    if round_item.allow_repeat:
        candidates = participants
    else:
        candidates = [item for item in participants if not item.is_winner]

    if not candidates:
        raise HTTPException(status_code=400, detail="当前没有可中奖的候选人")

    winner_count = min(round_item.count, len(candidates))
    lucky_dogs = random.sample(candidates, winner_count)
    winners_payload = []

    for participant in lucky_dogs:
        participant.is_winner = True
        participant.winning_lottery_id = round_item.id
        session.add(participant)

        winner_record = LotteryWinner(
            lottery_id=round_item.id,
            user_id=participant.user_id,
            user_name=participant.user_name,
        )
        session.add(winner_record)

        winners_payload.append(
            {
                "id": participant.user_id,
                "user_id": participant.user_id,
                "name": participant.user_name,
                "department": participant.department,
                "avatar": participant.avatar,
            }
        )

    round_item.status = LOTTERY_ROUND_FINISHED
    session.add(round_item)

    remaining_rounds = [
        item for item in get_rounds(meeting_id, session)
        if item.id != round_item.id and round_status(item) != LOTTERY_ROUND_FINISHED
    ]
    lottery_session.last_result = serialize_winners(winners_payload)
    lottery_session.session_status = LOTTERY_SESSION_COMPLETED if not remaining_rounds else LOTTERY_SESSION_RESULT
    lottery_session.updated_at = lottery_now()
    session.add(lottery_session)
    session.commit()

    return await _broadcast_snapshot(meeting_id, session)


@router.post("/{meeting_id}/reset")
async def reset_lottery_session(meeting_id: int, session: Session = Depends(get_session)):
    ensure_meeting_or_404(meeting_id, session)
    lottery_session = ensure_lottery_session(meeting_id, session)

    participants = session.exec(select(LotteryParticipant).where(LotteryParticipant.meeting_id == meeting_id)).all()
    for participant in participants:
        session.delete(participant)

    rounds = get_rounds(meeting_id, session)
    for round_item in rounds:
        round_item.status = LOTTERY_ROUND_DRAFT
        session.add(round_item)

    round_ids = [round_item.id for round_item in rounds]
    if round_ids:
        winners = session.exec(select(LotteryWinner).where(LotteryWinner.lottery_id.in_(round_ids))).all()
        for winner in winners:
            session.delete(winner)

    lottery_session.session_status = LOTTERY_SESSION_IDLE
    lottery_session.current_round_id = None
    lottery_session.self_service_locked = False
    lottery_session.last_result = None
    lottery_session.updated_at = lottery_now()
    session.add(lottery_session)
    session.commit()

    return await _broadcast_snapshot(meeting_id, session)
