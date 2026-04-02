"""
抽签服务层
统一封装抽签会话快照、轮次排序与参与池状态推导能力。
"""
import json
from datetime import datetime
from typing import List, Optional

from fastapi import HTTPException
from sqlmodel import Session, select

from models import Lottery, LotteryParticipant, LotterySession, LotteryWinner, Meeting


LOTTERY_SESSION_IDLE = "idle"
LOTTERY_SESSION_COLLECTING = "collecting"
LOTTERY_SESSION_READY = "ready"
LOTTERY_SESSION_ROLLING = "rolling"
LOTTERY_SESSION_RESULT = "result"
LOTTERY_SESSION_COMPLETED = "completed"

LOTTERY_ROUND_DRAFT = "draft"
LOTTERY_ROUND_READY = "ready"
LOTTERY_ROUND_FINISHED = "finished"


def lottery_now() -> datetime:
    return datetime.now()


def ensure_meeting_or_404(meeting_id: int, session: Session) -> Meeting:
    meeting = session.get(Meeting, meeting_id)
    if not meeting:
        raise HTTPException(status_code=404, detail="会议不存在")
    return meeting


def ensure_round_or_404(lottery_id: int, session: Session) -> Lottery:
    round_item = session.get(Lottery, lottery_id)
    if not round_item:
        raise HTTPException(status_code=404, detail="轮次不存在")
    return round_item


def ensure_lottery_session(meeting_id: int, session: Session) -> LotterySession:
    lottery_session = session.get(LotterySession, meeting_id)
    if lottery_session:
        return lottery_session

    lottery_session = LotterySession(
        meeting_id=meeting_id,
        session_status=LOTTERY_SESSION_IDLE,
        self_service_locked=False,
    )
    session.add(lottery_session)
    session.commit()
    session.refresh(lottery_session)
    return lottery_session


def normalized_round_status_value(status: Optional[str]) -> str:
    if status in {"pending", "waiting", "active", None, ""}:
        return LOTTERY_ROUND_DRAFT
    return status


def round_status(round_item: Lottery) -> str:
    return normalized_round_status_value(round_item.status)


def sort_rounds(rounds: List[Lottery]) -> List[Lottery]:
    return sorted(
        rounds,
        key=lambda item: (
            not isinstance(item.sort_order, int) or item.sort_order <= 0,
            item.sort_order if isinstance(item.sort_order, int) and item.sort_order > 0 else 10**9,
            item.created_at or datetime.min,
            item.id or 0,
        ),
    )


def normalize_round_orders(rounds: List[Lottery], session: Session) -> bool:
    dirty = False
    for index, round_item in enumerate(sort_rounds(rounds), start=1):
        if round_item.sort_order != index:
            round_item.sort_order = index
            session.add(round_item)
            dirty = True
    return dirty


def get_rounds(meeting_id: int, session: Session) -> List[Lottery]:
    rounds = session.exec(select(Lottery).where(Lottery.meeting_id == meeting_id)).all()
    return sort_rounds(rounds)


def get_joined_participants(meeting_id: int, session: Session) -> List[LotteryParticipant]:
    return session.exec(
        select(LotteryParticipant)
        .where(LotteryParticipant.meeting_id == meeting_id, LotteryParticipant.status == "joined")
        .order_by(LotteryParticipant.created_at, LotteryParticipant.user_id)
    ).all()


def get_next_round(rounds: List[Lottery], current_round_id: Optional[int] = None) -> Optional[Lottery]:
    if not rounds:
        return None

    unfinished_rounds = [item for item in rounds if round_status(item) != LOTTERY_ROUND_FINISHED]
    if not unfinished_rounds:
        return None
    if current_round_id is None:
        return unfinished_rounds[0]

    current_index = next((index for index, item in enumerate(rounds) if item.id == current_round_id), None)
    if current_index is None:
        return unfinished_rounds[0]

    for item in rounds[current_index + 1:]:
        if round_status(item) != LOTTERY_ROUND_FINISHED:
            return item

    return next((item for item in unfinished_rounds if item.id != current_round_id), None)


def resolve_round_for_roll(lottery_session: LotterySession, rounds: List[Lottery]) -> Optional[Lottery]:
    current_round = next((item for item in rounds if item.id == lottery_session.current_round_id), None) if lottery_session.current_round_id else None
    if current_round and round_status(current_round) != LOTTERY_ROUND_FINISHED:
        return current_round
    return get_next_round(rounds, current_round.id if current_round else None)


def serialize_winners(winners: List[dict]) -> str:
    return json.dumps(winners, ensure_ascii=False)


def deserialize_winners(raw: Optional[str]) -> List[dict]:
    if not raw:
        return []
    try:
        parsed = json.loads(raw)
        return parsed if isinstance(parsed, list) else []
    except Exception:
        return []


def resolve_session_status(meeting_id: int, session: Session, has_current_round: bool) -> str:
    joined_count = len(get_joined_participants(meeting_id, session))
    if has_current_round:
        return LOTTERY_SESSION_READY if joined_count > 0 else LOTTERY_SESSION_COLLECTING
    return LOTTERY_SESSION_IDLE if joined_count == 0 else LOTTERY_SESSION_COLLECTING


def build_round_payload(round_item: Lottery, display_sort_order: Optional[int] = None) -> dict:
    return {
        "id": round_item.id,
        "title": round_item.title,
        "count": round_item.count,
        "allow_repeat": round_item.allow_repeat,
        "sort_order": display_sort_order if display_sort_order is not None else round_item.sort_order,
        "status": round_status(round_item),
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


def build_participant_payload(participant: LotteryParticipant) -> dict:
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


def is_self_service_locked(
    lottery_session: LotterySession,
    rounds: Optional[List[Lottery]] = None,
) -> bool:
    if getattr(lottery_session, "self_service_locked", False):
        return True

    effective_rounds = rounds if rounds is not None else []
    if lottery_session.session_status in {
        LOTTERY_SESSION_ROLLING,
        LOTTERY_SESSION_RESULT,
        LOTTERY_SESSION_COMPLETED,
    }:
        return True
    return any(round_status(round_item) == LOTTERY_ROUND_FINISHED for round_item in effective_rounds)


def build_session_snapshot(meeting_id: int, session: Session, user_id: Optional[int] = None) -> dict:
    ensure_meeting_or_404(meeting_id, session)
    lottery_session = ensure_lottery_session(meeting_id, session)
    rounds = get_rounds(meeting_id, session)
    round_order_map = {round_item.id: index for index, round_item in enumerate(rounds, start=1)}
    joined_participants = get_joined_participants(meeting_id, session)
    current_round = next((item for item in rounds if item.id == lottery_session.current_round_id), None) if lottery_session.current_round_id else None
    next_round = get_next_round(rounds, current_round.id if current_round else None)

    session_status = lottery_session.session_status
    all_finished = bool(rounds) and all(round_status(round_item) == LOTTERY_ROUND_FINISHED for round_item in rounds)
    if all_finished and session_status not in {LOTTERY_SESSION_ROLLING, LOTTERY_SESSION_RESULT}:
        session_status = LOTTERY_SESSION_COMPLETED

    if current_round and round_status(current_round) == LOTTERY_ROUND_READY and session_status in {LOTTERY_SESSION_IDLE, LOTTERY_SESSION_COLLECTING}:
        session_status = LOTTERY_SESSION_READY if joined_participants else LOTTERY_SESSION_COLLECTING
    self_service_open = not is_self_service_locked(lottery_session, rounds)

    return {
        "meeting_id": meeting_id,
        "session_status": session_status,
        "current_round_id": current_round.id if current_round else None,
        "current_round": build_round_payload(current_round, round_order_map.get(current_round.id)) if current_round else None,
        "next_round_id": next_round.id if next_round else None,
        "next_round": build_round_payload(next_round, round_order_map.get(next_round.id)) if next_round else None,
        "participants": [build_participant_payload(item) for item in joined_participants],
        "participants_count": len(joined_participants),
        "winners": deserialize_winners(lottery_session.last_result),
        "joined": any(item.user_id == user_id for item in joined_participants) if user_id else False,
        "self_service_open": self_service_open,
        "all_rounds_finished": all_finished,
        "rounds": [build_round_payload(round_item, round_order_map.get(round_item.id)) for round_item in rounds],
    }


def recalculate_participant_winner_flags(meeting_id: int, session: Session, rounds: Optional[List[Lottery]] = None) -> bool:
    effective_rounds = rounds if rounds is not None else get_rounds(meeting_id, session)
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
