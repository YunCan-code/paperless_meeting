"""
Socket.IO 实时通信管理器
用于投票等实时功能的 WebSocket 通信
"""
import socketio
import os
import random
from datetime import datetime
from typing import Dict, Set

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


@sio.event
async def connect(sid, environ):
    """客户端连接"""
    print(f"[Socket.IO] Client connected: {sid}")


@sio.event
async def disconnect(sid):
    """客户端断开"""
    print(f"[Socket.IO] Client disconnected: {sid}")
    # 从所有房间移除
    for meeting_id, sids in meeting_rooms.items():
        sids.discard(sid)


@sio.event
async def join_meeting(sid, data):
    """加入会议房间"""
    meeting_id = data.get('meeting_id')
    if meeting_id:
        room = f"meeting_{meeting_id}"
        await sio.enter_room(sid, room)
        if meeting_id not in meeting_rooms:
            meeting_rooms[meeting_id] = set()
        meeting_rooms[meeting_id].add(sid)
        print(f"[Socket.IO] {sid} joined room {room}")
        return {"success": True}
    return {"success": False, "error": "missing meeting_id"}


@sio.event
async def leave_meeting(sid, data):
    """离开会议房间"""
    meeting_id = data.get('meeting_id')
    if meeting_id:
        room = f"meeting_{meeting_id}"
        await sio.leave_room(sid, room)
        if meeting_id in meeting_rooms:
            meeting_rooms[meeting_id].discard(sid)
        print(f"[Socket.IO] {sid} left room {room}")


async def broadcast_vote_start(meeting_id: int, vote_data: dict):
    """广播投票开始"""
    room = f"meeting_{meeting_id}"
    await sio.emit('vote_start', vote_data, room=room)
    print(f"[Socket.IO] Broadcasted vote_start to {room}")


async def broadcast_vote_update(meeting_id: int, vote_id: int, results: list):
    """广播投票更新"""
    room = f"meeting_{meeting_id}"
    await sio.emit('vote_update', {'vote_id': vote_id, 'results': results}, room=room)


async def broadcast_vote_end(meeting_id: int, vote_id: int, final_results: dict):
    """广播投票结束"""
    room = f"meeting_{meeting_id}"
    await sio.emit('vote_end', {'vote_id': vote_id, **final_results}, room=room)
    print(f"[Socket.IO] Broadcasted vote_end to {room}")


# 抽签状态管理
# meeting_id -> {
#    'participants': dict,  # id -> user_info (内存池)
#    'history': set,        # set of user_id (winners from DB + memory)
#    'current_config': dict
# }
lottery_states: Dict[int, dict] = {}


def get_db_session():
    return SQLSession(engine)


@sio.event
async def lottery_action(sid, data):
    """抽签动作处理"""
    action = data.get('action')
    meeting_id = data.get('meeting_id')
    
    if not meeting_id:
        return
        
    room = f"meeting_{meeting_id}"
    
    # 初始化内存状态 (从DB加载历史中奖者避免重启后重复中奖)
    if meeting_id not in lottery_states:
        history_set = set()
        try:
            with get_db_session() as session:
                # 加载该会议的所有历史中奖者ID
                statement = select(LotteryWinner.user_id).join(Lottery).where(Lottery.meeting_id == meeting_id)
                results = session.exec(statement).all()
                for uid in results:
                    history_set.add(uid)
        except Exception as e:
            print(f"[Lottery] Error loading history: {e}")
            
        lottery_states[meeting_id] = {
            'participants': {},
            'history': history_set,
            'current_config': {}
        }
    
    state = lottery_states[meeting_id]
    
    # 1. 准备/配置本轮
    if action == 'prepare':
        lottery_id = data.get('lottery_id')
        
        # 如果指定了 lottery_id (从预设列表启动)
        if lottery_id:
            with get_db_session() as session:
                lot = session.get(Lottery, lottery_id)
                if lot:
                    title = lot.title
                    count = lot.count
                    allow_repeat = lot.allow_repeat
                    # 更新内存中的当前配置
                    state['current_config'] = {
                        'lottery_id': lot.id,
                        'title': title,
                        'count': count,
                        'allow_repeat': allow_repeat
                    }
        else:
            # 临时新建 (旧逻辑)
            with get_db_session() as session:
                count_stmt = select(Lottery).where(Lottery.meeting_id == meeting_id)
                existing_count = len(session.exec(count_stmt).all())
                
            default_title = f"第 {existing_count + 1} 轮"
            title = data.get('title', default_title)
            count = data.get('count', 1)
            allow_repeat = data.get('allow_repeat', False)
            
            state['current_config'] = {
                'title': title,
                'count': count,
                'allow_repeat': allow_repeat
            }
        
        # 计算连接池 (通用逻辑)
        cfg = state['current_config']
        pool_size = len(state['participants'])
        if not cfg.get('allow_repeat'):
            pool_size -= len(state['history'])
        
        # 计算轮次信息和历史中奖名单
        round_index = 1
        total_rounds = 1
        history_winners = []
        
        try:
            with get_db_session() as session:
                # 获取所有轮次
                all_rounds = session.exec(
                    select(Lottery).where(Lottery.meeting_id == meeting_id).order_by(Lottery.id)
                ).all()
                total_rounds = len(all_rounds)
                
                # 计算当前轮次索引
                current_lottery_id = cfg.get('lottery_id')
                for i, lot in enumerate(all_rounds):
                    if lot.id == current_lottery_id:
                        round_index = i + 1
                        break
                
                # 获取已完成轮次的中奖名单
                for lot in all_rounds:
                    if lot.status == 'finished':
                        winners_stmt = select(LotteryWinner).where(LotteryWinner.lottery_id == lot.id)
                        lot_winners = session.exec(winners_stmt).all()
                        if lot_winners:
                            history_winners.append({
                                'title': lot.title,
                                'winners': [{'id': w.user_id, 'name': w.user_name} for w in lot_winners]
                            })
        except Exception as e:
            print(f"[Lottery] Error getting round info: {e}")
            
        await sio.emit('lottery_prepare', {
            'title': cfg['title'],
            'count': cfg['count'],
            'allow_repeat': cfg['allow_repeat'],
            'pool_size': max(0, pool_size),
            'round_index': round_index,
            'total_rounds': total_rounds,
            'history_winners': history_winners
        }, room=room)
        print(f"[Lottery] Meeting {meeting_id} prepared round {round_index}/{total_rounds}: {cfg['title']}")

    # 新增: 批量添加轮次 (Batch Add)
    elif action == 'batch_add':
        rounds = data.get('rounds', []) # [{title, count, allow_repeat}, ...]
        if rounds:
            try:
                with get_db_session() as session:
                    for r in rounds:
                        new_lot = Lottery(
                            meeting_id=meeting_id,
                            title=r.get('title'),
                            count=r.get('count', 1),
                            allow_repeat=r.get('allow_repeat', False),
                            status="pending"
                        )
                        session.add(new_lot)
                    session.commit()
                # 广播通知前端刷新列表
                await sio.emit('lottery_list_update', {}, room=room)
            except Exception as e:
                print(f"[Lottery] Batch add error: {e}")

    # 删除抽签轮次
    elif action == 'delete':
        lottery_id = data.get('lottery_id')
        if lottery_id:
            try:
                with get_db_session() as session:
                    # 先删除关联的中奖记录
                    from sqlmodel import delete as sql_delete
                    session.exec(sql_delete(LotteryWinner).where(LotteryWinner.lottery_id == lottery_id))
                    # 再删除轮次本身
                    lottery = session.get(Lottery, lottery_id)
                    if lottery:
                        session.delete(lottery)
                    session.commit()
                # 广播刷新
                await sio.emit('lottery_list_update', {}, room=room)
                print(f"[Lottery] Deleted round {lottery_id}")
            except Exception as e:
                print(f"[Lottery] Delete error: {e}")

    # 更新抽签轮次
    elif action == 'update':
        lottery_id = data.get('lottery_id')
        if lottery_id:
            try:
                with get_db_session() as session:
                    lottery = session.get(Lottery, lottery_id)
                    if lottery and lottery.status != 'finished':
                        if 'title' in data:
                            lottery.title = data['title']
                        if 'count' in data:
                            lottery.count = data['count']
                        if 'allow_repeat' in data:
                            lottery.allow_repeat = data['allow_repeat']
                        session.commit()
                # 广播刷新
                await sio.emit('lottery_list_update', {}, room=room)
                print(f"[Lottery] Updated round {lottery_id}")
            except Exception as e:
                print(f"[Lottery] Update error: {e}")

    # 2. 用户报名
    elif action == 'join':
        user_info = data.get('user')
        if not user_info:
            return
            
        user_id = user_info['id']
        state['participants'][user_id] = user_info
        
        await sio.emit('lottery_players_update', {
            'count': len(state['participants']),
            'latest_user': user_info
        }, room=room)
        print(f"[Lottery] User {user_id} joined meeting {meeting_id}")

    # 移除参与者
    elif action == 'remove_participant':
        user_id = data.get('user_id')
        if user_id and user_id in state['participants']:
            del state['participants'][user_id]
            await sio.emit('lottery_players_update', {
                'count': len(state['participants']),
                'removed_user_id': user_id
            }, room=room)
            print(f"[Lottery] User {user_id} removed from meeting {meeting_id}")

    # 3. 开始滚动
    elif action == 'start':
        await sio.emit('lottery_start', {}, room=room)

    # 4. 停止并生成结果 (写入DB)
    elif action == 'stop':
        config = state.get('current_config', {})
        count = config.get('count', 1)
        allow_repeat = config.get('allow_repeat', False)
        title = config.get('title', '新一轮抽签')
        pre_id = config.get('id') # 如果是预设的
        
        candidates = list(state['participants'].values())
        if not allow_repeat:
            candidates = [u for u in candidates if u['id'] not in state['history']]
            
        winners = []
        if candidates:
            k = min(len(candidates), count)
            winners = random.sample(candidates, k)
            timestamp = datetime.now()
            
            # DB Persistence
            try:
                with get_db_session() as session:
                    lottery = None
                    # 如果是预设的Pending轮次，更新它
                    if pre_id:
                        lottery = session.get(Lottery, pre_id)
                        if lottery:
                            lottery.status = "finished"
                            lottery.created_at = timestamp # Update finish time? Or keep create time? 
                            # Usually keep create time, maybe add finished_at? 
                            # Re-using created_at as 'event time' for now.
                            session.add(lottery)
                    
                    # 如果不是预设，或者是临时轮次
                    if not lottery:
                        lottery = Lottery(
                            meeting_id=meeting_id,
                            title=title,
                            count=count,
                            allow_repeat=allow_repeat,
                            status="finished",
                            created_at=timestamp
                        )
                        session.add(lottery)
                    
                    session.commit()
                    session.refresh(lottery)
                    
                    # Create Winners
                    for w in winners:
                        # Update memory history
                        state['history'].add(w['id'])
                        # DB
                        winner_record = LotteryWinner(
                            lottery_id=lottery.id,
                            user_id=w['id'],
                            winning_at=timestamp
                        )
                        session.add(winner_record)
                    session.commit()
            except Exception as e:
                print(f"[Lottery] Save error: {e}")

        await sio.emit('lottery_stop', {
            'winners': winners,
            'remaining_candidates': len(candidates) - len(winners)
        }, room=room)
        # 通知列表刷新 (状态变更为finished)
        await sio.emit('lottery_list_update', {}, room=room)
        print(f"[Lottery] Stop round, winners: {len(winners)}")

    # 5. 获取历史记录 (从DB读取，包含Pending)
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


# 创建 ASGI 应用 (将在 main.py 中与 FastAPI 合并)
socket_app = socketio.ASGIApp(sio)
