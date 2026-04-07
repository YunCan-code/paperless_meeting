"""
Socket.IO 实时通信管理器
用于投票等实时功能的 WebSocket 通信
"""
import json
import socketio
import os
from typing import Dict, Set, Optional

# 导入数据库依赖
from sqlmodel import Session as SQLSession
try:
    from backend.database import engine
    from backend.models import LotteryParticipant, Lottery, LotterySession
except ImportError:
    from database import engine
    from models import LotteryParticipant, Lottery, LotterySession

# 获取 Redis URL (用于多 Worker 模式下的跨进程通信)
REDIS_URL = os.environ.get('REDIS_URL')

# 创建 Socket.IO 服务器实例 (ASGI模式)
# 如果配置了 Redis，使用 Redis 作为消息管理器，支持多 Worker
if REDIS_URL:
    mgr = socketio.AsyncRedisManager(REDIS_URL)
    sio = socketio.AsyncServer(
        async_mode='asgi',
        cors_allowed_origins='*',
        client_manager=mgr,
        logger=True,
        engineio_logger=True
    )
    print(f"[Socket.IO] Using Redis manager: {REDIS_URL}")
else:
    sio = socketio.AsyncServer(
        async_mode='asgi',
        cors_allowed_origins='*',
        logger=True,
        engineio_logger=True
    )
    print("[Socket.IO] Using in-memory manager (single worker only)")

# 会议房间管理
meeting_rooms: Dict[int, Set[str]] = {}  # meeting_id -> set of sid


def get_db_session():
    return SQLSession(engine)

from sqlmodel import select

def get_db_participants(meeting_id: int) -> list:
    """从数据库获取当前抽签参与者"""
    participants = []
    try:
        with get_db_session() as session:
            stmt = select(LotteryParticipant).where(
                LotteryParticipant.meeting_id == meeting_id,
                LotteryParticipant.status == "joined"
            )
            print(f"[Lottery] Querying participants for meeting_id={meeting_id}, status='joined'")
            results = session.exec(stmt).all()
            print(f"[Lottery] Query returned {len(results)} results")
            for p in results:
                participants.append({
                    "id": p.user_id, # 注意：这里用 user_id (int)
                    "user_id": p.user_id,
                    "name": p.user_name,
                    "avatar": p.avatar,
                    "department": p.department,
                    "is_winner": p.is_winner, # 返回中奖状态
                    "winning_lottery_id": p.winning_lottery_id, # 返回中奖轮次ID
                    "status": p.status,
                    "created_at": p.created_at.isoformat() if p.created_at else None,
                })
    except Exception as e:
        print(f"[Lottery] DB Get Participants Error: {e}")
    return participants


# --- 标准 Socket.IO 事件 ---

@sio.event
async def connect(sid, environ):
    print(f"[Socket.IO] Client connected: {sid}")

@sio.event
async def disconnect(sid):
    print(f"[Socket.IO] Client disconnected: {sid}")

@sio.on('join_meeting')
async def join_meeting(sid, data):
    """加入会议房间"""
    meeting_id = data.get('meeting_id')
    if meeting_id:
        room = f"meeting_{meeting_id}"
        await sio.enter_room(sid, room)
        
        if meeting_id not in meeting_rooms:
            meeting_rooms[meeting_id] = set()
        meeting_rooms[meeting_id].add(sid)
        
        print(f"[Socket.IO] {sid} joined room: {room}")

@sio.on('leave_meeting')
async def leave_meeting(sid, data):
    """离开会议房间"""
    meeting_id = data.get('meeting_id')
    if meeting_id:
        room = f"meeting_{meeting_id}"
        await sio.leave_room(sid, room)
        if meeting_id in meeting_rooms and sid in meeting_rooms[meeting_id]:
            meeting_rooms[meeting_id].discard(sid)


# --- 投票相关广播 ---

async def broadcast_vote_state(meeting_id: int, vote_data: dict):
    room = f"meeting_{meeting_id}"
    await sio.emit('vote_state_change', vote_data, room=room)

    # 兼容旧前端事件
    status = vote_data.get("status")
    if status in {"countdown", "active"}:
        await sio.emit(
            'vote_start',
            {
                "id": vote_data.get("id"),
                "title": vote_data.get("title"),
                "duration_seconds": vote_data.get("duration_seconds"),
                "started_at": vote_data.get("started_at"),
                "wait_seconds": vote_data.get("countdown_remaining_seconds") or 0,
            },
            room=room,
        )
    if status == "closed":
        await sio.emit(
            'vote_end',
            {
                "vote_id": vote_data.get("id"),
                "results": {
                    "vote_id": vote_data.get("id"),
                    "title": vote_data.get("title"),
                    "total_voters": vote_data.get("total_voters", 0),
                    "results": vote_data.get("results", []),
                },
            },
            room=room,
        )


async def broadcast_vote_results(meeting_id: int, vote_id: int, result_data: dict):
    room = f"meeting_{meeting_id}"
    await sio.emit('vote_results_change', result_data, room=room)
    await sio.emit(
        'vote_update',
        {
            'vote_id': vote_id,
            'results': result_data.get('results', []),
        },
        room=room,
    )


async def broadcast_vote_start(meeting_id: int, vote_data: dict):
    await broadcast_vote_state(meeting_id, vote_data)


async def broadcast_vote_end(meeting_id: int, vote_id: int, final_results: dict):
    room = f"meeting_{meeting_id}"
    await sio.emit('vote_end', {'vote_id': vote_id, 'results': final_results}, room=room)


async def broadcast_vote_update(meeting_id: int, vote_id: int, results: list):
    room = f"meeting_{meeting_id}"
    await sio.emit('vote_update', {'vote_id': vote_id, 'results': results}, room=room)

async def broadcast_meeting_changed(action: str, meeting_data: Optional[dict] = None):
    payload = {"action": action}
    if meeting_data:
        payload.update(meeting_data)
    await sio.emit('meeting_changed', payload)


async def broadcast_media_changed(action: str, media_data: Optional[dict] = None):
    payload = {"action": action}
    if media_data:
        payload.update(media_data)
    await sio.emit('media_changed', payload)


def _normalize_round_status_value(status: Optional[str]) -> str:
    return "draft" if status in {"pending", "waiting", "active"} else (status or "draft")


def _build_lottery_round_payload(round_item: Lottery) -> dict:
    return {
        "id": round_item.id,
        "title": round_item.title,
        "count": round_item.count,
        "allow_repeat": round_item.allow_repeat,
        "status": _normalize_round_status_value(round_item.status),
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


def _get_lottery_session_snapshot(meeting_id: int) -> dict:
    with get_db_session() as session:
        meeting_session = session.get(LotterySession, meeting_id)
        participants = get_db_participants(meeting_id)
        rounds = session.exec(select(Lottery).where(Lottery.meeting_id == meeting_id).order_by(Lottery.created_at)).all()
        dirty = False
        for round_item in rounds:
            normalized_status = _normalize_round_status_value(round_item.status)
            if round_item.status != normalized_status:
                round_item.status = normalized_status
                session.add(round_item)
                dirty = True
        if dirty:
            session.commit()
            rounds = session.exec(select(Lottery).where(Lottery.meeting_id == meeting_id).order_by(Lottery.created_at)).all()

        current_round = None
        if meeting_session and meeting_session.current_round_id:
            current_round = session.get(Lottery, meeting_session.current_round_id)

        winners = []
        if meeting_session and meeting_session.last_result:
            try:
                winners = json.loads(meeting_session.last_result)
            except Exception:
                winners = []

        session_status = meeting_session.session_status if meeting_session else "idle"
        all_finished = bool(rounds) and all(_normalize_round_status_value(round_item.status) == "finished" for round_item in rounds)
        if current_round and _normalize_round_status_value(current_round.status) == "ready" and session_status in {"idle", "collecting"}:
            session_status = "ready" if participants else "collecting"
        if all_finished and session_status not in {"rolling", "result"}:
            session_status = "completed"

        return {
            "meeting_id": meeting_id,
            "session_status": session_status,
            "current_round_id": current_round.id if current_round else None,
            "current_round": _build_lottery_round_payload(current_round) if current_round else None,
            "participants": participants,
            "participants_count": len(participants),
            "winners": winners,
            "joined": False,
            "all_rounds_finished": all_finished,
            "rounds": [_build_lottery_round_payload(round_item) for round_item in rounds],
        }


async def broadcast_lottery_session_change(meeting_id: int, payload: Optional[dict] = None):
    room = f"meeting_{meeting_id}"
    snapshot = payload or _get_lottery_session_snapshot(meeting_id)
    await sio.emit('lottery_session_change', snapshot, room=room)


# Create ASGI App
socket_app = socketio.ASGIApp(sio)
