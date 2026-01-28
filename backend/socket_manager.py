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

@sio.on('get_lottery_state')
async def handle_get_lottery_state(sid, data):
    """响应客户端的抽签状态请求"""
    meeting_id = data.get('meeting_id')
    if not meeting_id:
        await sio.emit('lottery_error', {'message': '缺少会议ID'}, to=sid)
        return
    
    state = get_or_init_lottery_state(meeting_id)
    payload = {
        "status": state["status"],
        "participants": list(state["participants"].values()),
        "current_title": state.get("current_title"),
        "current_count": state.get("current_count", 1),
        "winners": state.get("winners", []),
        "participant_count": len(state["participants"])
    }
    await sio.emit('lottery_state_change', payload, to=sid)
    print(f"[Lottery] Sent state to {sid}: {state['status']}")

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


# ========== 抽签功能 ==========

# 抽签状态枚举
class LotteryState:
    IDLE = "IDLE"           # 空闲/未开始
    PREPARING = "PREPARING" # 准备中 (等待加入)
    ROLLING = "ROLLING"     # 滚动中 (已锁定)
    RESULT = "RESULT"       # 结果展示

# 内存中的抽签状态 (meeting_id -> state)
lottery_states: Dict[int, dict] = {}

def get_or_init_lottery_state(meeting_id: int) -> dict:
    """获取或初始化抽签状态"""
    if meeting_id not in lottery_states:
        lottery_states[meeting_id] = {
            "status": LotteryState.IDLE,
            "participants": {},  # user_id -> {name, sid}
            "current_lottery_id": None,
            "current_title": None,
            "current_count": 1,
            "winners": [],
            "history_winners": set()  # 已中奖用户ID集合
        }
    return lottery_states[meeting_id]


async def broadcast_lottery_state(meeting_id: int, state: dict):
    """广播抽签状态变化"""
    room = f"meeting_{meeting_id}"
    payload = {
        "status": state["status"],
        "participants": list(state["participants"].values()),
        "current_title": state.get("current_title"),
        "current_count": state.get("current_count", 1),
        "winners": state.get("winners", []),
        "participant_count": len(state["participants"])
    }
    await sio.emit('lottery_state_change', payload, room=room)
    print(f"[Lottery] Broadcast state: {state['status']} to room {room}")


@sio.on('lottery_action')
async def lottery_action(sid, data):
    """处理抽签动作"""
    action = data.get('action')
    meeting_id = data.get('meeting_id')
    
    if not meeting_id:
        await sio.emit('lottery_error', {'message': '缺少会议ID'}, to=sid)
        return
    
    state = get_or_init_lottery_state(meeting_id)
    
    print(f"[Lottery] Action: {action}, Meeting: {meeting_id}, Status: {state['status']}")
    
    # ===== JOIN: 用户加入抽签池 =====
    if action == 'join':
        user_id = data.get('user_id')
        user_name = data.get('user_name', '匿名用户')
        
        # 检查状态
        if state["status"] == LotteryState.ROLLING:
            await sio.emit('lottery_error', {'message': '抽签进行中，无法加入'}, to=sid)
            return
        
        # 检查是否已中奖
        if user_id in state["history_winners"]:
            await sio.emit('lottery_error', {'message': '您已中奖，无法再次参与'}, to=sid)
            return
        
        # 如果是 IDLE 状态，自动切换到 PREPARING
        if state["status"] == LotteryState.IDLE:
            state["status"] = LotteryState.PREPARING
        
        # 添加参与者
        state["participants"][user_id] = {"id": user_id, "name": user_name, "sid": sid}
        
        await broadcast_lottery_state(meeting_id, state)
        await sio.emit('lottery_joined', {'user_id': user_id}, to=sid)
    
    # ===== QUIT: 用户退出抽签池 =====
    elif action == 'quit':
        user_id = data.get('user_id')
        
        if state["status"] == LotteryState.ROLLING:
            await sio.emit('lottery_error', {'message': '抽签进行中，无法退出'}, to=sid)
            return
        
        if user_id in state["participants"]:
            del state["participants"][user_id]
            await broadcast_lottery_state(meeting_id, state)
            await sio.emit('lottery_quit', {'user_id': user_id}, to=sid)
    
    # ===== PREPARE: 管理员准备抽签 =====
    elif action == 'prepare':
        lottery_id = data.get('lottery_id')
        title = data.get('title', '抽签')
        count = data.get('count', 1)
        
        state["status"] = LotteryState.PREPARING
        state["current_lottery_id"] = lottery_id
        state["current_title"] = title
        state["current_count"] = count
        state["winners"] = []
        
        await broadcast_lottery_state(meeting_id, state)
    
    # ===== ROLL: 开始抽签 =====
    elif action == 'roll':
        if state["status"] != LotteryState.PREPARING:
            await sio.emit('lottery_error', {'message': '当前状态无法开始抽签'}, to=sid)
            return
        
        if len(state["participants"]) == 0:
            await sio.emit('lottery_error', {'message': '没有参与者'}, to=sid)
            return
        
        # 锁定状态
        state["status"] = LotteryState.ROLLING
        await broadcast_lottery_state(meeting_id, state)
    
    # ===== STOP: 停止抽签并抽取中奖者 =====
    elif action == 'stop':
        if state["status"] != LotteryState.ROLLING:
            await sio.emit('lottery_error', {'message': '抽签未在进行中'}, to=sid)
            return
        
        import random
        
        participants = list(state["participants"].values())
        count = min(state["current_count"], len(participants))
        
        # 随机抽取中奖者
        winners = random.sample(participants, count)
        state["winners"] = winners
        state["status"] = LotteryState.RESULT
        
        # 记录中奖者到历史
        for w in winners:
            state["history_winners"].add(w["id"])
            # 从参与者池移除
            if w["id"] in state["participants"]:
                del state["participants"][w["id"]]
        
        # 保存到数据库
        try:
            from models import Lottery, LotteryWinner
            with get_db_session() as db:
                lottery_id = state.get("current_lottery_id")
                if lottery_id:
                    lottery = db.get(Lottery, lottery_id)
                    if lottery:
                        lottery.status = "finished"
                        for w in winners:
                            winner_record = LotteryWinner(
                                lottery_id=lottery_id,
                                user_id=w.get("id") if isinstance(w.get("id"), int) else None,
                                user_name=w["name"]
                            )
                            db.add(winner_record)
                        db.commit()
        except Exception as e:
            print(f"[Lottery] DB save error: {e}")
        
        await broadcast_lottery_state(meeting_id, state)
    
    # ===== RESET: 重置抽签状态 =====
    elif action == 'reset':
        state["status"] = LotteryState.IDLE
        state["participants"] = {}
        state["winners"] = []
        state["current_lottery_id"] = None
        state["current_title"] = None
        state["history_winners"] = set()
        
        await broadcast_lottery_state(meeting_id, state)
    
    # ===== ADMIN_ADD: 管理员添加参与者 =====
    elif action == 'admin_add':
        user_name = data.get('user_name')
        if not user_name:
            await sio.emit('lottery_error', {'message': '缺少用户名'}, to=sid)
            return
        
        import uuid
        temp_id = f"temp_{uuid.uuid4().hex[:8]}"
        state["participants"][temp_id] = {"id": temp_id, "name": user_name, "sid": None}
        
        if state["status"] == LotteryState.IDLE:
            state["status"] = LotteryState.PREPARING
        
        await broadcast_lottery_state(meeting_id, state)
    
    else:
        await sio.emit('lottery_error', {'message': f'未知动作: {action}'}, to=sid)


@sio.on('get_lottery_state')
async def get_lottery_state_handler(sid, data):
    """获取当前抽签状态 (用于客户端同步)"""
    meeting_id = data.get('meeting_id')
    if not meeting_id:
        return
    
    state = get_or_init_lottery_state(meeting_id)
    payload = {
        "status": state["status"],
        "participants": list(state["participants"].values()),
        "current_title": state.get("current_title"),
        "current_count": state.get("current_count", 1),
        "winners": state.get("winners", []),
        "participant_count": len(state["participants"])
    }
    await sio.emit('lottery_state_change', payload, to=sid)


# Create ASGI App
socket_app = socketio.ASGIApp(sio)
