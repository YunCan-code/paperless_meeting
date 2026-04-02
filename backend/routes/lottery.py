"""
抽签功能 API
数据库会话为真相源，Socket 仅广播状态快照。
"""
import json
import random
from datetime import datetime
from typing import List, Optional

from fastapi import APIRouter, Depends, HTTPException
from pydantic import BaseModel
from sqlmodel import Session, select

from database import get_session
from models import Lottery, LotteryParticipant, LotterySession, LotteryWinner, Meeting, User
from socket_manager import broadcast_lottery_session_change

router = APIRouter(prefix="/lottery", tags=["lottery"])

LOTTERY_SESSION_IDLE = "idle"
LOTTERY_SESSION_COLLECTING = "collecting"
LOTTERY_SESSION_READY = "ready"
LOTTERY_SESSION_ROLLING = "rolling"
LOTTERY_SESSION_RESULT = "result"
LOTTERY_SESSION_COMPLETED = "completed"

LOTTERY_ROUND_DRAFT = "draft"
LOTTERY_ROUND_READY = "ready"
LOTTERY_ROUND_FINISHED = "finished"


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


def _now() -> datetime:
    return datetime.now()


def _ensure_meeting_or_404(meeting_id: int, session: Session) -> Meeting:
    meeting = session.get(Meeting, meeting_id)
    if not meeting:
        raise HTTPException(status_code=404, detail="会议不存在")
    return meeting


def _ensure_round_or_404(lottery_id: int, session: Session) -> Lottery:
    round_item = session.get(Lottery, lottery_id)
    if not round_item:
        raise HTTPException(status_code=404, detail="轮次不存在")
    return round_item


def _ensure_session(meeting_id: int, session: Session) -> LotterySession:
    lottery_session = session.get(LotterySession, meeting_id)
    if lottery_session:
        return lottery_session

    lottery_session = LotterySession(meeting_id=meeting_id, session_status=LOTTERY_SESSION_IDLE)
    session.add(lottery_session)
    session.commit()
    session.refresh(lottery_session)
    return lottery_session


def _normalized_round_status_value(status: Optional[str]) -> str:
    if status in {"pending", "waiting", "active", None, ""}:
        return LOTTERY_ROUND_DRAFT
    return status


def _round_status(round_item: Lottery) -> str:
    return _normalized_round_status_value(round_item.status)


def _sort_rounds(rounds: List[Lottery]) -> List[Lottery]:
    return sorted(
        rounds,
        key=lambda item: (
            not isinstance(item.sort_order, int) or item.sort_order <= 0,
            item.sort_order if isinstance(item.sort_order, int) and item.sort_order > 0 else 10**9,
            item.created_at or datetime.min,
            item.id or 0,
        ),
    )


def _normalize_round_orders(rounds: List[Lottery], session: Session) -> bool:
    dirty = False
    for index, round_item in enumerate(_sort_rounds(rounds), start=1):
        if round_item.sort_order != index:
            round_item.sort_order = index
            session.add(round_item)
            dirty = True
    return dirty


def _get_next_round(rounds: List[Lottery], current_round_id: Optional[int] = None) -> Optional[Lottery]:
    if not rounds:
        return None

    unfinished_rounds = [item for item in rounds if _round_status(item) != LOTTERY_ROUND_FINISHED]
    if not unfinished_rounds:
        return None
    if current_round_id is None:
        return unfinished_rounds[0]

    current_index = next((index for index, item in enumerate(rounds) if item.id == current_round_id), None)
    if current_index is None:
        return unfinished_rounds[0]

    for item in rounds[current_index + 1:]:
        if _round_status(item) != LOTTERY_ROUND_FINISHED:
            return item

    return next((item for item in unfinished_rounds if item.id != current_round_id), None)


def _resolve_round_for_roll(lottery_session: LotterySession, rounds: List[Lottery]) -> Optional[Lottery]:
    current_round = next((item for item in rounds if item.id == lottery_session.current_round_id), None) if lottery_session.current_round_id else None
    if current_round and _round_status(current_round) != LOTTERY_ROUND_FINISHED:
        return current_round
    next_round = _get_next_round(rounds, current_round.id if current_round else None)
    return next_round


def _serialize_winners(winners: List[dict]) -> str:
    return json.dumps(winners, ensure_ascii=False)


def _deserialize_winners(raw: Optional[str]) -> List[dict]:
    if not raw:
        return []
    try:
        parsed = json.loads(raw)
        return parsed if isinstance(parsed, list) else []
    except Exception:
        return []


def _get_rounds(meeting_id: int, session: Session) -> List[Lottery]:
    rounds = session.exec(select(Lottery).where(Lottery.meeting_id == meeting_id)).all()
    return _sort_rounds(rounds)


def _get_joined_participants(meeting_id: int, session: Session) -> List[LotteryParticipant]:
    return session.exec(
        select(LotteryParticipant)
        .where(LotteryParticipant.meeting_id == meeting_id, LotteryParticipant.status == "joined")
        .order_by(LotteryParticipant.created_at, LotteryParticipant.user_id)
    ).all()


def _resolve_session_status(meeting_id: int, session: Session, has_current_round: bool) -> str:
    joined_count = len(_get_joined_participants(meeting_id, session))
    if has_current_round:
        return LOTTERY_SESSION_READY if joined_count > 0 else LOTTERY_SESSION_COLLECTING
    return LOTTERY_SESSION_IDLE if joined_count == 0 else LOTTERY_SESSION_COLLECTING


def _build_round_payload(round_item: Lottery, display_sort_order: Optional[int] = None) -> dict:
    return {
        "id": round_item.id,
        "title": round_item.title,
        "count": round_item.count,
        "allow_repeat": round_item.allow_repeat,
        "sort_order": display_sort_order if display_sort_order is not None else round_item.sort_order,
        "status": _round_status(round_item),
        "created_at": round_item.created_at.isoformat() if round_item.created_at else None,
        "winners": [
            {
                "id": winner.id,
                "user_id": winner.user_id,
                "user_name": winner.user_name,
                "winning_at": winner.winning_at.isoformat() if winner.winning_at else None,
            }
            for winner in sorted(round_item.winners, key=lambda item: item.winning_at)
        ],
    }


def _build_participant_payload(participant: LotteryParticipant) -> dict:
    return {
        "id": participant.user_id,
        "user_id": participant.user_id,
        "name": participant.user_name,
        "avatar": participant.avatar,
        "department": participant.department,
        "status": participant.status,
        "is_winner": participant.is_winner,
        "winning_lottery_id": participant.winning_lottery_id,
        "created_at": participant.created_at.isoformat() if participant.created_at else None,
    }


def _build_session_snapshot(meeting_id: int, session: Session, user_id: Optional[int] = None) -> dict:
    _ensure_meeting_or_404(meeting_id, session)
    lottery_session = _ensure_session(meeting_id, session)
    rounds = _get_rounds(meeting_id, session)
    round_order_map = {round_item.id: index for index, round_item in enumerate(rounds, start=1)}
    joined_participants = _get_joined_participants(meeting_id, session)
    current_round = next((item for item in rounds if item.id == lottery_session.current_round_id), None) if lottery_session.current_round_id else None
    next_round = _get_next_round(rounds, current_round.id if current_round else None)

    session_status = lottery_session.session_status
    all_finished = bool(rounds) and all(_round_status(round_item) == LOTTERY_ROUND_FINISHED for round_item in rounds)
    if all_finished and session_status not in {LOTTERY_SESSION_ROLLING, LOTTERY_SESSION_RESULT}:
        session_status = LOTTERY_SESSION_COMPLETED

    if current_round and _round_status(current_round) == LOTTERY_ROUND_READY and session_status in {LOTTERY_SESSION_IDLE, LOTTERY_SESSION_COLLECTING}:
        session_status = LOTTERY_SESSION_READY if joined_participants else LOTTERY_SESSION_COLLECTING

    snapshot = {
        "meeting_id": meeting_id,
        "session_status": session_status,
        "current_round_id": current_round.id if current_round else None,
        "current_round": _build_round_payload(current_round, round_order_map.get(current_round.id)) if current_round else None,
        "next_round_id": next_round.id if next_round else None,
        "next_round": _build_round_payload(next_round, round_order_map.get(next_round.id)) if next_round else None,
        "participants": [_build_participant_payload(item) for item in joined_participants],
        "participants_count": len(joined_participants),
        "winners": _deserialize_winners(lottery_session.last_result),
        "joined": any(item.user_id == user_id for item in joined_participants) if user_id else False,
        "all_rounds_finished": all_finished,
        "rounds": [_build_round_payload(round_item, round_order_map.get(round_item.id)) for round_item in rounds],
    }
    return snapshot


def _recalculate_participant_winner_flags(meeting_id: int, session: Session, rounds: Optional[List[Lottery]] = None) -> bool:
    effective_rounds = rounds if rounds is not None else _get_rounds(meeting_id, session)
    round_ids = [round_item.id for round_item in effective_rounds if round_item.id is not None]
    remaining_winners = []
    if round_ids:
        remaining_winners = session.exec(
            select(LotteryWinner).where(LotteryWinner.lottery_id.in_(round_ids))
        ).all()

    latest_winning_round_by_user = {}
    for winner in sorted(remaining_winners, key=lambda item: (item.winning_at or datetime.min, item.id or 0)):
        if winner.user_id is not None:
            latest_winning_round_by_user[winner.user_id] = winner.lottery_id

    dirty = False
    participants = session.exec(select(LotteryParticipant).where(LotteryParticipant.meeting_id == meeting_id)).all()
    for participant in participants:
        winning_lottery_id = latest_winning_round_by_user.get(participant.user_id)
        is_winner = winning_lottery_id is not None
        if participant.is_winner != is_winner or participant.winning_lottery_id != winning_lottery_id:
            participant.is_winner = is_winner
            participant.winning_lottery_id = winning_lottery_id
            session.add(participant)
            dirty = True
    return dirty


async def _broadcast_snapshot(meeting_id: int, session: Session) -> dict:
    snapshot = _build_session_snapshot(meeting_id, session)
    await broadcast_lottery_session_change(meeting_id, snapshot)
    return snapshot


@router.get("/{meeting_id}/history")
def get_lottery_history(meeting_id: int, session: Session = Depends(get_session)):
    meeting = _ensure_meeting_or_404(meeting_id, session)
    rounds = _get_rounds(meeting_id, session)
    round_order_map = {round_item.id: index for index, round_item in enumerate(rounds, start=1)}
    return {
        "meeting_id": meeting_id,
        "meeting_title": meeting.title,
        "rounds": [_build_round_payload(round_item, round_order_map.get(round_item.id)) for round_item in rounds],
    }


@router.get("/{meeting_id}/rounds")
def get_lottery_rounds(meeting_id: int, session: Session = Depends(get_session)):
    _ensure_meeting_or_404(meeting_id, session)
    rounds = _get_rounds(meeting_id, session)
    round_order_map = {round_item.id: index for index, round_item in enumerate(rounds, start=1)}
    return {"items": [_build_round_payload(round_item, round_order_map.get(round_item.id)) for round_item in rounds]}


@router.get("/{meeting_id}/session")
def get_lottery_session(meeting_id: int, user_id: Optional[int] = None, session: Session = Depends(get_session)):
    return _build_session_snapshot(meeting_id, session, user_id=user_id)


@router.post("/{meeting_id}/round")
async def create_lottery_round(
    meeting_id: int,
    request: LotteryCreateRequest,
    session: Session = Depends(get_session),
):
    _ensure_meeting_or_404(meeting_id, session)
    if not request.title.strip():
        raise HTTPException(status_code=400, detail="轮次名称不能为空")
    if request.count <= 0:
        raise HTTPException(status_code=400, detail="中奖人数必须大于 0")

    existing_rounds = _get_rounds(meeting_id, session)
    if _normalize_round_orders(existing_rounds, session):
        session.commit()
        existing_rounds = _get_rounds(meeting_id, session)
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
    snapshot = _build_round_payload(round_item)
    await _broadcast_snapshot(meeting_id, session)
    return snapshot


@router.put("/round/{lottery_id}")
async def update_lottery_round(
    lottery_id: int,
    request: LotteryRoundUpdateRequest,
    session: Session = Depends(get_session),
):
    round_item = _ensure_round_or_404(lottery_id, session)
    if _round_status(round_item) == LOTTERY_ROUND_FINISHED:
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
    snapshot = _build_round_payload(round_item)
    await _broadcast_snapshot(round_item.meeting_id, session)
    return snapshot


@router.delete("/round/{lottery_id}")
async def delete_lottery_round(lottery_id: int, session: Session = Depends(get_session)):
    round_item = _ensure_round_or_404(lottery_id, session)
    meeting_id = round_item.meeting_id
    lottery_session = _ensure_session(meeting_id, session)

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
        lottery_session.session_status = _resolve_session_status(meeting_id, session, has_current_round=False)
        lottery_session.last_result = None
        lottery_session.updated_at = _now()
        session.add(lottery_session)

    session.delete(round_item)
    session.commit()
    remaining_rounds = session.exec(select(Lottery).where(Lottery.meeting_id == meeting_id)).all()
    dirty = False
    if _normalize_round_orders(remaining_rounds, session):
        dirty = True
    if _recalculate_participant_winner_flags(meeting_id, session, rounds=remaining_rounds):
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
    _ensure_meeting_or_404(meeting_id, session)
    lottery_session = _ensure_session(meeting_id, session)
    if lottery_session.session_status == LOTTERY_SESSION_ROLLING:
        raise HTTPException(status_code=400, detail="抽签进行中，暂时不能加入")

    user = session.get(User, request.user_id)
    if not user:
        raise HTTPException(status_code=404, detail="用户不存在")

    participant = session.get(LotteryParticipant, (meeting_id, request.user_id))
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

    current_round = session.get(Lottery, lottery_session.current_round_id) if lottery_session.current_round_id else None
    if not (current_round and _round_status(current_round) == LOTTERY_ROUND_FINISHED and lottery_session.session_status in {LOTTERY_SESSION_RESULT, LOTTERY_SESSION_COMPLETED}):
        lottery_session.session_status = _resolve_session_status(
            meeting_id,
            session,
            has_current_round=bool(lottery_session.current_round_id),
        )
    lottery_session.updated_at = _now()
    session.add(lottery_session)
    session.commit()

    return await _broadcast_snapshot(meeting_id, session)


@router.post("/{meeting_id}/participants/quit")
async def quit_lottery_pool(
    meeting_id: int,
    request: LotteryParticipantActionRequest,
    session: Session = Depends(get_session),
):
    _ensure_meeting_or_404(meeting_id, session)
    lottery_session = _ensure_session(meeting_id, session)
    if lottery_session.session_status == LOTTERY_SESSION_ROLLING:
        raise HTTPException(status_code=400, detail="抽签进行中，暂时不能退出")

    participant = session.get(LotteryParticipant, (meeting_id, request.user_id))
    if not participant:
        raise HTTPException(status_code=404, detail="参与者不存在")

    participant.status = "left"
    session.add(participant)

    joined_count_after = max(0, len(_get_joined_participants(meeting_id, session)) - 1)
    current_round = session.get(Lottery, lottery_session.current_round_id) if lottery_session.current_round_id else None
    if current_round and _round_status(current_round) == LOTTERY_ROUND_FINISHED and lottery_session.session_status in {LOTTERY_SESSION_RESULT, LOTTERY_SESSION_COMPLETED}:
        lottery_session.session_status = lottery_session.session_status
    elif lottery_session.current_round_id:
        lottery_session.session_status = LOTTERY_SESSION_READY if joined_count_after > 0 else LOTTERY_SESSION_COLLECTING
    else:
        lottery_session.session_status = LOTTERY_SESSION_IDLE if joined_count_after == 0 else LOTTERY_SESSION_COLLECTING
    lottery_session.updated_at = _now()
    session.add(lottery_session)
    session.commit()

    return await _broadcast_snapshot(meeting_id, session)


@router.post("/{meeting_id}/prepare")
async def prepare_lottery_round(
    meeting_id: int,
    request: LotteryPrepareRequest,
    session: Session = Depends(get_session),
):
    _ensure_meeting_or_404(meeting_id, session)
    lottery_session = _ensure_session(meeting_id, session)
    round_item = _ensure_round_or_404(request.lottery_id, session)
    if round_item.meeting_id != meeting_id:
        raise HTTPException(status_code=400, detail="轮次与会议不匹配")

    if _round_status(round_item) == LOTTERY_ROUND_FINISHED:
        raise HTTPException(status_code=400, detail="该轮次已完成")

    for candidate in _get_rounds(meeting_id, session):
        if candidate.id != round_item.id and _round_status(candidate) == LOTTERY_ROUND_READY:
            candidate.status = LOTTERY_ROUND_DRAFT
            session.add(candidate)

    round_item.status = LOTTERY_ROUND_READY
    lottery_session.current_round_id = round_item.id
    lottery_session.session_status = _resolve_session_status(meeting_id, session, has_current_round=True)
    lottery_session.last_result = None
    lottery_session.updated_at = _now()

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
    round_item = _ensure_round_or_404(lottery_id, session)
    lottery_session = _ensure_session(round_item.meeting_id, session)

    if lottery_session.session_status == LOTTERY_SESSION_ROLLING:
        raise HTTPException(status_code=400, detail="抽签进行中，暂时不能调整顺序")
    if _round_status(round_item) == LOTTERY_ROUND_FINISHED:
        raise HTTPException(status_code=400, detail="已完成轮次不允许调整顺序")
    if lottery_session.current_round_id == round_item.id:
        raise HTTPException(status_code=400, detail="当前轮次不允许调整顺序")
    if request.direction not in {"up", "down"}:
        raise HTTPException(status_code=400, detail="移动方向无效")

    rounds = _get_rounds(round_item.meeting_id, session)
    if _normalize_round_orders(rounds, session):
        session.commit()
        rounds = _get_rounds(round_item.meeting_id, session)
    current_index = next((index for index, item in enumerate(rounds) if item.id == round_item.id), None)
    if current_index is None:
        raise HTTPException(status_code=404, detail="轮次不存在")

    target_index = current_index - 1 if request.direction == "up" else current_index + 1
    if target_index < 0 or target_index >= len(rounds):
        raise HTTPException(status_code=400, detail="当前轮次已无法继续移动")

    target_round = rounds[target_index]
    if _round_status(target_round) == LOTTERY_ROUND_FINISHED:
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
    _ensure_meeting_or_404(meeting_id, session)
    lottery_session = _ensure_session(meeting_id, session)
    rounds = _get_rounds(meeting_id, session)
    round_item = _resolve_round_for_roll(lottery_session, rounds)
    if not round_item:
        raise HTTPException(status_code=400, detail="当前没有可开始的轮次")

    participants = _get_joined_participants(meeting_id, session)
    if not participants:
        raise HTTPException(status_code=400, detail="当前没有可参与抽签的人员")
    if lottery_session.session_status not in {LOTTERY_SESSION_COLLECTING, LOTTERY_SESSION_READY, LOTTERY_SESSION_RESULT, LOTTERY_SESSION_COMPLETED}:
        raise HTTPException(status_code=400, detail="当前状态无法开始抽签")

    lottery_session.current_round_id = round_item.id
    lottery_session.session_status = LOTTERY_SESSION_ROLLING
    lottery_session.updated_at = _now()
    round_item.status = LOTTERY_ROUND_READY
    session.add(round_item)
    session.add(lottery_session)
    session.commit()

    return await _broadcast_snapshot(meeting_id, session)


@router.post("/{meeting_id}/stop")
async def stop_lottery_roll(meeting_id: int, session: Session = Depends(get_session)):
    _ensure_meeting_or_404(meeting_id, session)
    lottery_session = _ensure_session(meeting_id, session)
    if lottery_session.session_status != LOTTERY_SESSION_ROLLING:
        raise HTTPException(status_code=400, detail="抽签未在进行中")
    if not lottery_session.current_round_id:
        raise HTTPException(status_code=400, detail="当前没有准备好的轮次")

    round_item = _ensure_round_or_404(lottery_session.current_round_id, session)
    participants = _get_joined_participants(meeting_id, session)
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
        item for item in _get_rounds(meeting_id, session)
        if item.id != round_item.id and _round_status(item) != LOTTERY_ROUND_FINISHED
    ]
    lottery_session.last_result = _serialize_winners(winners_payload)
    lottery_session.session_status = LOTTERY_SESSION_COMPLETED if not remaining_rounds else LOTTERY_SESSION_RESULT
    lottery_session.updated_at = _now()
    session.add(lottery_session)
    session.commit()

    return await _broadcast_snapshot(meeting_id, session)


@router.post("/{meeting_id}/reset")
async def reset_lottery_session(meeting_id: int, session: Session = Depends(get_session)):
    _ensure_meeting_or_404(meeting_id, session)
    lottery_session = _ensure_session(meeting_id, session)

    participants = session.exec(select(LotteryParticipant).where(LotteryParticipant.meeting_id == meeting_id)).all()
    for participant in participants:
        session.delete(participant)

    rounds = _get_rounds(meeting_id, session)
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
    lottery_session.last_result = None
    lottery_session.updated_at = _now()
    session.add(lottery_session)
    session.commit()

    return await _broadcast_snapshot(meeting_id, session)
