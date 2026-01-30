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
    from backend.models import User, LotteryParticipant, Lottery, LotteryWinner
except ImportError:
    from database import engine
    from models import User, LotteryParticipant, Lottery, LotteryWinner

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
                    "name": p.user_name,
                    "avatar": p.avatar,
                    "department": p.department,
                    "avatar": p.avatar,
                    "department": p.department,
                    "is_winner": p.is_winner, # 返回中奖状态
                    "winning_lottery_id": p.winning_lottery_id, # 返回中奖轮次ID
                    "sid": None # DB中没有sid，不过前端展示主要靠 id/name
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

@sio.on('get_lottery_state')
async def handle_get_lottery_state(sid, data):
    """响应客户端的抽签状态请求"""
    meeting_id = data.get('meeting_id')
    if not meeting_id:
        await sio.emit('lottery_error', {'message': '缺少会议ID'}, to=sid)
        return
    
    state = get_or_init_lottery_state(meeting_id)
    
    # [核心修改] 从数据库获取真实列表
    db_participants = get_db_participants(meeting_id)
    
    # 检查自己是否在列表中
    is_joined = False
    if data.get('user_id'):
        is_joined = any(str(p['id']) == str(data.get('user_id')) for p in db_participants)
        
    response = {
        'status': state['status'],
        'participant_count': len(db_participants),
        'participants': db_participants, # 返回持久化数据
        'config': {
            'title': state.get('current_title'),
            'count': state.get('current_count', 1)
        },
        'last_result': state.get('winners', []),
        'is_joined': is_joined
    }
    await sio.emit('lottery_state_sync', response, room=sid)
    print(f"[Lottery] Sent state sync to {sid}: {state['status']}")

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
    # [Modified] Fetch participants from DB for broadcast
    db_participants = get_db_participants(meeting_id)
    
    payload = {
        "status": state["status"],
        "participants": db_participants,
        "current_title": state.get("current_title"),
        "current_count": state.get("current_count", 1),
        "winners": state.get("winners", []),
        "participant_count": len(db_participants)
    }
    await sio.emit('lottery_state_change', payload, room=room)
    print(f"[Lottery] Broadcast state: {state['status']} to room {room}, count: {len(db_participants)}")


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
        user_dept = data.get('department', '')
        user_avatar = data.get('avatar', '')
        
        # 检查状态
        if state["status"] == LotteryState.ROLLING:
            await sio.emit('lottery_error', {'message': '抽签进行中，无法加入'}, to=sid)
            return
        
        # 检查是否已中奖 (内存检查 + DB检查)
        if user_id in state["history_winners"]:
            await sio.emit('lottery_error', {'message': '您已中奖，无法再次参与'}, to=sid)
            return
        
        # [Modified] 写入数据库
        try:
            with get_db_session() as session:
                # 检查是否已存在
                participant = session.get(LotteryParticipant, (meeting_id, user_id))
                if not participant:
                    participant = LotteryParticipant(
                        meeting_id=meeting_id,
                        user_id=user_id,
                        user_name=user_name,
                        department=user_dept,
                        avatar=user_avatar,
                        status="joined"
                    )
                    session.add(participant)
                else:
                    if participant.is_winner:
                        await sio.emit('lottery_error', {'message': '您已中奖，无法再次参与'}, to=sid)
                        return
                    participant.status = "joined" # 重新加入
                    participant.user_name = user_name
                    participant.department = user_dept
                    participant.avatar = user_avatar
                    session.add(participant)
                session.commit()
                print(f"[Lottery] User {user_id} ({user_name}) joined successfully, status=joined")
        except Exception as e:
            print(f"[Lottery] Join DB Error: {e}")
            import traceback
            traceback.print_exc()

        # 如果是 IDLE 状态，自动切换到 PREPARING
        if state["status"] == LotteryState.IDLE:
            state["status"] = LotteryState.PREPARING
        
        # 更新内存 (Optional)
        state["participants"][user_id] = {"id": user_id, "name": user_name, "sid": sid}
        
        # 广播更新 (读取最新 DB 数据广播给所有人)
        room = f"meeting_{meeting_id}"
        print(f"[Lottery] Fetching participants for meeting {meeting_id} from DB...")
        current_list = get_db_participants(meeting_id)
        print(f"[Lottery] Found {len(current_list)} participants in DB: {[p['name'] for p in current_list]}")
        await sio.emit('lottery_players_update', {
            'participant_count': len(current_list),
            'participants': current_list
        }, room=room)
    
    # ===== QUIT: 用户退出抽签池 =====
    elif action == 'quit':
        user_id = data.get('user_id')
        
        if state["status"] == LotteryState.ROLLING:
            await sio.emit('lottery_error', {'message': '抽签进行中，无法退出'}, to=sid)
            return
        
        # [Modified] Update DB
        try:
            with get_db_session() as session:
                participant = session.get(LotteryParticipant, (meeting_id, user_id))
                if participant:
                    participant.status = "left"
                    session.add(participant)
                    session.commit()
        except Exception as e:
             print(f"[Lottery] Quit DB Error: {e}")

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
        
        # Check DB count
        db_participants = get_db_participants(meeting_id)
        if len(db_participants) == 0:
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
        
        # [Modified] 从数据库筛选合格候选人
        candidates = []
        try:
            with get_db_session() as session:
                stmt = select(LotteryParticipant).where(
                    LotteryParticipant.meeting_id == meeting_id,
                    LotteryParticipant.status == "joined",
                    LotteryParticipant.is_winner == False
                )
                candidates = session.exec(stmt).all()
        except Exception as e:
            print(f"[Lottery] Stop DB Query Error: {e}")
            
        count = min(state["current_count"], len(candidates))
        
        # 随机抽取中奖者
        lucky_dogs = random.sample(candidates, count)
        
        winners_data = []
        
        try:
            with get_db_session() as session:
                lottery_id = state.get("current_lottery_id")
                # Update Lottery status
                if lottery_id:
                     lottery = session.get(Lottery, lottery_id)
                     if lottery:
                         lottery.status = "finished"
                         session.add(lottery)

                for dog in lucky_dogs:
                    # 1. Update Participant
                    dog.is_winner = True
                    session.add(dog)
                    
                    # Store in winners_data for frontend
                    winners_data.append({
                        "id": dog.user_id,
                        "name": dog.user_name,
                        "department": dog.department,
                        "avatar": dog.avatar
                    })
                    
                    # 2. Add to LotteryWinner table
                    if lottery_id:
                        winner_record = LotteryWinner(
                            lottery_id=lottery_id,
                            user_id=dog.user_id,
                            user_name=dog.user_name
                        )
                        session.add(winner_record)
                    
                    # 3. Update LotteryParticipant is_winner status
                    participant = session.get(LotteryParticipant, (meeting_id, dog.user_id))
                    if participant:
                        participant.is_winner = True
                        participant.winning_lottery_id = lottery_id # 记录中奖轮次
                        session.add(participant)
                    
                    # Update memory history
                    state["history_winners"].add(dog.user_id)

                session.commit()
        except Exception as e:
             print(f"[Lottery] Save Winner Error: {e}")

        state["winners"] = winners_data
        state["status"] = LotteryState.RESULT
        
        # Update memory participants just in case
        state["participants"] = {p["id"]: p for p in get_db_participants(meeting_id)}
        
        await broadcast_lottery_state(meeting_id, state)
    
    # ===== RESET: 重置抽签状态 =====
    elif action == 'reset':
        state["status"] = LotteryState.IDLE
        state["participants"] = {}
        state["winners"] = []
        state["current_lottery_id"] = None
        state["current_title"] = None
        state["history_winners"] = set()
        
        # [Modified] Clear DB participants for this meeting
        try:
             with get_db_session() as session:
                stmt = select(LotteryParticipant).where(LotteryParticipant.meeting_id == meeting_id)
                results = session.exec(stmt).all()
                for p in results:
                    session.delete(p)
                session.commit()
        except Exception as e:
            print(f"[Lottery] Reset DB Error: {e}")
        
        await broadcast_lottery_state(meeting_id, state)
    
    # ===== ADMIN_ADD: 管理员添加参与者 =====
    elif action == 'admin_add':
        # Admin add is tricky with DB because we need a user_id. 
        # If it's a "guest", we might need a negative ID or a temp logic.
        # For now, let's assume admin adds existing users or we skip persistence for pure unregistered temps if that was the case,
        # BUT the original code used uuid. 
        # To persist, we need a unique ID.
        # Let's keep the original logic BUT try to create a dummy user? 
        # Or just support REAL users for now as per `LotteryParticipant` having `user_id` FK.
        # Wait, `LotteryParticipant` has `user_id` as Primary Key and Foreign Key to `user.id`.
        # So we cannot insert random strings like "temp_uuid".
        # If admin_add sends a real user_id, fine.
        # If admin_add is for "Virtual People", we might fail FK constraint.
        # Let's see original code: `temp_id = f"temp_{uuid.uuid4().hex[:8]}"`
        # This implies it DOES support non-DB users.
        # Attempting to insert this into LotteryParticipant will FAIL if user_id is FK.
        # The prompt solution `doc/抽签10` defines `user_id: int = Field(foreign_key="user.id", primary_key=True)`.
        # So we CANNOT support temp strings.
        # I will comment out ADMIN_ADD or make it error if not a real user.
        # Or I can just leave it in memory (but it won't persist).
        # Given the task is about "join" (real users), I will disable admin_add for temp users or warn about it.
        # Original code seemed to support it. 
        # I will leave admin_add in MEMORY ONLY for now to avoid crashing, but warn it won't persist.
        # actually, if I mix memory and DB participants in broadcast, it might be confusing.
        # `get_db_participants` only returns DB ones.
        # So admin_add'ed temp users will disappear on refresh. 
        # That's acceptable for "temp" users, or I should fix it properly by allowing null FK.
        # But `LotteryParticipant` PK is `user_id`.
        # I'll comment it out or return error "Not supported in persistent mode yet".
        
        await sio.emit('lottery_error', {'message': '暂不支持添加临时用户'}, to=sid)

    else:
        await sio.emit('lottery_error', {'message': f'未知动作: {action}'}, to=sid)


# Create ASGI App
socket_app = socketio.ASGIApp(sio)
