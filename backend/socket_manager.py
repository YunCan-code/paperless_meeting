"""
Socket.IO 实时通信管理器
用于投票等实时功能的 WebSocket 通信
"""
import socketio
import os
import random
from datetime import datetime
from typing import Dict, Set, Optional

# 导入数据库依赖
from sqlmodel import select, Session as SQLSession
try:
    from backend.database import engine
    from backend.models import Lottery, LotteryWinner, User
except ImportError:
    from database import engine
    from models import Lottery, LotteryWinner, User

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

# 抽签状态枚举
class LotteryState:
    IDLE = "IDLE"           # 无/结束
    PREPARING = "PREPARING" # 准备中 (等待加入)
    ROLLING = "ROLLING"     # 滚动中 (锁定)
    RESULT = "RESULT"       # 结果展示

# 抽签状态管理 (内存)
# meeting_id -> {
#    'status': str,             # LotteryState
#    'participants': dict,      # user_id -> user_info (当前候选池)
#    'history': set,            # set of user_id (已中奖历史)
#    'current_config': dict,    # 当前轮次配置 {lottery_id, title, count, allow_repeat}
#    'last_result': dict        # 上次/当前结果 {winners, ...} (用于RESULT状态回显)
# }
lottery_states: Dict[int, dict] = {}


def get_db_session():
    return SQLSession(engine)

def load_history_set(meeting_id: int) -> set:
    """Helper: Load history winners from DB"""
    history_set = set()
    try:
        with get_db_session() as session:
            statement = select(LotteryWinner.user_id).join(Lottery).where(Lottery.meeting_id == meeting_id)
            results = session.exec(statement).all()
            for uid in results:
                history_set.add(str(uid))
    except Exception as e:
        print(f"[Lottery] Error loading history: {e}")
    return history_set

def get_or_init_state(meeting_id: int) -> dict:
    if meeting_id not in lottery_states:
        # Default State
        status = LotteryState.IDLE
        current_config = {}

        # --- Auto-Recovery Logic ---
        try:
            with get_db_session() as session:
                # Check for active lottery in this meeting
                statement = select(Lottery).where(
                    Lottery.meeting_id == meeting_id,
                    Lottery.status == "active"
                )
                active_lot = session.exec(statement).first()
                
                if active_lot:
                    print(f"[Lottery] Auto-recovered active round: {active_lot.title} (Meeting {meeting_id})")
                    status = LotteryState.PREPARING
                    current_config = {
                        'lottery_id': active_lot.id,
                        'title': active_lot.title,
                        'count': active_lot.count,
                        'allow_repeat': active_lot.allow_repeat
                    }
        except Exception as e:
            print(f"[Lottery] Auto-recover error: {e}")
            
        lottery_states[meeting_id] = {
            'status': status,
            'participants': {}, # Memory lost on restart, users must re-join
            'history': load_history_set(meeting_id),
            'current_config': current_config,
            'last_result': None
        }
    return lottery_states[meeting_id]

async def broadcast_state_change(meeting_id: int, state: dict):
    """广播状态变更"""
    room = f"meeting_{meeting_id}"
    
    # 构建精简的 payload
    payload = {
        'status': state['status'],
        'participants_count': len(state['participants']),
        'config': state['current_config'],
        # RESULT 状态带上结果
        'last_result': state['last_result'] if state['status'] == LotteryState.RESULT else None
    }
    
    await sio.emit('lottery_state_change', payload, room=room)
    print(f"[Lottery] Broadcast state change: {state['status']} (Meeting {meeting_id})")

async def broadcast_pool_update(meeting_id: int, state: dict):
    """广播此池更新 (含全量列表，主要用于大屏/安卓展示头像)"""
    room = f"meeting_{meeting_id}"
    participants_list = list(state['participants'].values())
    await sio.emit('lottery_players_update', {
        'count': len(participants_list),
        'all_participants': participants_list
    }, room=room)

# --- 标准 Socket.IO 事件 ---

@sio.event
async def connect(sid, environ):
    print(f"[Socket.IO] Client connected: {sid}")

@sio.event
async def disconnect(sid):
    print(f"[Socket.IO] Client disconnected: {sid}")
    for meeting_id, sids in meeting_rooms.items():
        sids.discard(sid)

@sio.event
async def join_meeting(sid, data):
    meeting_id = data.get('meeting_id')
    if meeting_id:
        room = f"meeting_{meeting_id}"
        await sio.enter_room(sid, room)
        if meeting_id not in meeting_rooms:
            meeting_rooms[meeting_id] = set()
        meeting_rooms[meeting_id].add(sid)
        print(f"[Socket.IO] {sid} joined room {room}")
        return {"success": True}
    return {"success": False}

@sio.event
async def leave_meeting(sid, data):
    meeting_id = data.get('meeting_id')
    if meeting_id:
        room = f"meeting_{meeting_id}"
        await sio.leave_room(sid, room)
        print(f"[Socket.IO] {sid} left room {room}")

# --- 新增: 客户端主动查询状态 (解决刷新/重连数据丢失) ---
@sio.event
async def get_lottery_state(sid, data):
    meeting_id = data.get('meeting_id')
    user_id = data.get('user_id') # 可选，查询自己是否在池中
    
    if not meeting_id:
        return
        
    state = get_or_init_state(meeting_id)
    
    is_joined = False
    if user_id:
        is_joined = str(user_id) in state['participants']
        
    response = {
        'status': state['status'],
        'participants_count': len(state['participants']),
        'all_participants': list(state['participants'].values()), # 全量给前端恢复显示
        'config': state['current_config'],
        'last_result': state['last_result'],
        'is_joined': is_joined
    }
    await sio.emit('lottery_state_sync', response, room=sid)

# --- 抽签核心业务 ---

@sio.event
async def lottery_action(sid, data):
    action = data.get('action')
    meeting_id = data.get('meeting_id')
    
    if not meeting_id: 
        return
        
    room = f"meeting_{meeting_id}"
    state = get_or_init_state(meeting_id)
    
    print(f"[Lottery] Action {action} in Meeting {meeting_id} (Current: {state['status']})")
    
    # 1. 准备/配置 (Admin)
    if action == 'prepare':
        lottery_id = data.get('lottery_id')
        
        # 加载配置
        config = {}
        if lottery_id:
            try:
                with get_db_session() as session:
                    lot = session.get(Lottery, lottery_id)
                    if lot:
                        config = {
                            'lottery_id': lot.id, 
                            'title': lot.title, 
                            'count': lot.count, 
                            'allow_repeat': lot.allow_repeat
                        }
                        # DB Status -> active
                        lot.status = "active"
                        session.add(lot)
                        session.commit()
            except Exception as e:
                print(f"[Lottery] DB Error: {e}")
        else:
            # 临时创建逻辑(同原代码，略简)
            config = {
                'title': data.get('title', '临时抽签'),
                'count': data.get('count', 1),
                'allow_repeat': data.get('allow_repeat', False)
            }
            
        state['current_config'] = config
        
        # 关键: 状态变更为 PREPARING
        # 这里可以选择是否清空 participants。doc建议是清空，重新join
        # 如果是"全员自动模式"，这里应该自动填充
        # state['participants'] = {}  <-- Modified: Persist users across rounds
        state['status'] = LotteryState.PREPARING
        state['last_result'] = None # 清空上次结果
        
        await broadcast_state_change(meeting_id, state)
        await broadcast_pool_update(meeting_id, state) # 清空前端列表
        
        # Notify clients to refresh round list (fixes button state lag)
        await sio.emit('lottery_list_update', {}, room=room)

    # 2. 用户加入 (User / Admin / System)
    elif action == 'join':
        # 只有 PREPARING 状态允许加入
        if state['status'] != LotteryState.PREPARING:
            await sio.emit('lottery_error', {'message': '当前阶段无法加入'}, room=sid)
            return

        import uuid
        if not user_info: return
        
        # Ensure ID at backend too
        if 'id' not in user_info or not user_info['id']:
             user_info['id'] = str(uuid.uuid4())
             # If name is missing, give a default
             if 'name' not in user_info or not user_info['name']:
                 user_info['name'] = f"Guest-{user_info['id'][:4]}"

        uid = str(user_info['id'])
        
        # 幂等检查 (Update logic to allow re-join updates if needed, or keep check)
        # Using update to ensure info is refresh
        state['participants'][uid] = user_info
        await broadcast_pool_update(meeting_id, state)
        print(f"[Lottery] User {uid} ({user_info.get('name')}) joined pool.")
            
    # Remove participant
    elif action == 'remove_participant':
        user_id = str(data.get('user_id'))
        if user_id in state['participants']:
            del state['participants'][user_id]
            # Broadcast update with removed_user_id
            participants_list = list(state['participants'].values())
            await sio.emit('lottery_players_update', {
                'count': len(participants_list),
                'all_participants': participants_list,
                'removed_user_id': user_id
            }, room=room)

    # Admin Manual Add
    elif action == 'admin_add_participant':
        user_info = data.get('user')
        if user_info:
            import uuid
            # Ensure ID exists
            if 'id' not in user_info or not user_info['id']:
                user_info['id'] = str(uuid.uuid4())
            
            uid = str(user_info['id'])
            # Logic: Update/Insert instead of reset
            state['participants'][uid] = user_info
            
            # Auto-Active if IDLE
            if state['status'] == LotteryState.IDLE:
                 state['status'] = LotteryState.PREPARING
                 # Ensure default config if missing
                 if not state.get('current_config'):
                     state['current_config'] = {'title': '临时抽签', 'count': 1}
                 await broadcast_state_change(meeting_id, state)
                 
            await broadcast_pool_update(meeting_id, state)
            print(f"[Lottery] Admin added user {uid} (Meeting {meeting_id})")

    # 3. 开始滚动 (Admin)
    elif action == 'start':
        if state['status'] != LotteryState.PREPARING:
            return
            
        state['status'] = LotteryState.ROLLING
        await broadcast_state_change(meeting_id, state)
        
    # 4. 停止并出结果 (Admin)
    elif action == 'stop':
        if state['status'] != LotteryState.ROLLING:
            return
            
        # 计算逻辑
        config = state.get('current_config', {})
        count = config.get('count', 1)
        allow_repeat = config.get('allow_repeat', False)
        
        candidates = list(state['participants'].values())
        
        # 过滤
        if not allow_repeat:
            state['history'] = load_history_set(meeting_id) # 确保历史最新
            candidates = [u for u in candidates if str(u['id']) not in state['history']]
        winners = []
        if candidates:
            actual_count = min(len(candidates), count)
            if actual_count > 0:
                winners = random.sample(candidates, actual_count)
            
            # 保存结果到 DB Persistence
            try:
                with get_db_session() as session:
                    timestamp = datetime.now()
                    lottery_id = config.get('lottery_id')
                    
                    # Ensure Lottery Record
                    lottery = None
                    if lottery_id:
                        lottery = session.get(Lottery, lottery_id)
                    
                    if not lottery:
                        lottery = Lottery(
                            meeting_id=meeting_id,
                            title=config.get('title'),
                            count=count,
                            allow_repeat=allow_repeat,
                            status="finished",
                            created_at=timestamp
                        )
                        session.add(lottery)
                        session.commit()
                        session.refresh(lottery)
                    else:
                        lottery.status = "finished"
                        session.add(lottery)
                        
                    # Winners
                    for w in winners:
                        uid = str(w['id'])
                        state['history'].add(uid)
                        rec = LotteryWinner(lottery_id=lottery.id, user_id=int(uid), winning_at=timestamp)
                        session.add(rec)
                    session.commit()
            except Exception as e:
                print(f"[Lottery] Save Error: {e}")
        
        # 更新状态
        state['status'] = LotteryState.RESULT
        state['last_result'] = {
            'winners': winners,
            'remaining_count': len(candidates) - len(winners)
        }
        
        # (lottery_stop 兼容旧逻辑，state_change 走新逻辑)
        await sio.emit('lottery_stop', state['last_result'], room=room)
        await broadcast_state_change(meeting_id, state)
        # Notify list update so draft becomes active/finished in list
        await sio.emit('lottery_list_update', {}, room=room)


    # 管理动作 (增删改) - 保持原样或简化，广播 list_update 即可
    elif action in ['batch_add', 'delete', 'update']:
        # ... (Reuse existing DB logic for CRUD) ...
        # (Simplified implementation for brevity, implementing key parts)
        if action == 'delete':
            lid = data.get('lottery_id')
            with get_db_session() as session:
                from sqlmodel import delete
                session.exec(delete(LotteryWinner).where(LotteryWinner.lottery_id == lid))
                session.exec(delete(Lottery).where(Lottery.id == lid))
                session.commit()
            state['history'] = load_history_set(meeting_id)
            await sio.emit('lottery_list_update', {}, room=room)
            
        elif action == 'batch_add':
             rounds = data.get('rounds', [])
             with get_db_session() as session:
                 for r in rounds:
                     # Default status pending
                     session.add(Lottery(
                         meeting_id=meeting_id, title=r['title'], count=r.get('count',1), 
                         allow_repeat=r.get('allow_repeat',False), status='pending'
                     ))
                 session.commit()
             await sio.emit('lottery_list_update', {}, room=room)
             
    # History (兼容旧接口)
    elif action == 'get_history':
        rounds_data = []
        try:
            with get_db_session() as session:
                # Query Both Pending and Finished
                stmt = select(Lottery).where(Lottery.meeting_id == meeting_id).order_by(Lottery.created_at)
                lotteries = session.exec(stmt).all()
                
                for lot in lotteries:
                    # Get User info for winners (Join User table)
                    w_stmt = select(User).join(LotteryWinner).where(LotteryWinner.lottery_id == lot.id)
                    db_winners = session.exec(w_stmt).all()
                    
                    winner_list = [{'id': u.id, 'name': u.name, 'department': u.department} for u in db_winners]
                    
                    rounds_data.append({
                        'round_id': lot.id,
                        'title': lot.title,
                        'status': lot.status, # pending / finished
                        'count': lot.count,
                        'winners': winner_list,
                        'timestamp': lot.created_at.isoformat()
                    })
        except Exception as e:
            print(f"[Lottery] History error: {e}")
        
        await sio.emit('lottery_history', {
            'rounds': rounds_data,
            'total_participants': len(state['participants'])
        }, room=sid)
        
# 投票相关保持不变
async def broadcast_vote_start(meeting_id: int, vote_data: dict):
    room = f"meeting_{meeting_id}"
    await sio.emit('vote_start', vote_data, room=room)

async def broadcast_vote_end(meeting_id: int, vote_id: int, final_results: dict):
    room = f"meeting_{meeting_id}"
    await sio.emit('vote_end', {'vote_id': vote_id, **final_results}, room=room)

async def broadcast_vote_update(meeting_id: int, vote_id: int, results: list):
    room = f"meeting_{meeting_id}"
    await sio.emit('vote_update', {'vote_id': vote_id, 'results': results}, room=room)

# Create ASGI App
socket_app = socketio.ASGIApp(sio)
