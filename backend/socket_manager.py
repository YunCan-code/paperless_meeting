"""
Socket.IO 实时通信管理器
用于投票等实时功能的 WebSocket 通信
"""
import socketio
import os
from typing import Dict, Set

# 导入数据库依赖
from sqlmodel import Session as SQLSession
try:
    from backend.database import engine
    from backend.models import User
except ImportError:
    from database import engine
    from models import User

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

async def broadcast_vote_start(meeting_id: int, vote_data: dict):
    room = f"meeting_{meeting_id}"
    await sio.emit('vote_start', vote_data, room=room)

async def broadcast_vote_end(meeting_id: int, vote_id: int, final_results: dict):
    room = f"meeting_{meeting_id}"
    await sio.emit('vote_end', {'vote_id': vote_id, 'results': final_results}, room=room)

async def broadcast_vote_update(meeting_id: int, vote_id: int, results: list):
    room = f"meeting_{meeting_id}"
    await sio.emit('vote_update', {'vote_id': vote_id, 'results': results}, room=room)


# Create ASGI App
socket_app = socketio.ASGIApp(sio)
