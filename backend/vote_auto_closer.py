"""
自动检测并关闭过期投票的后台任务
"""
import asyncio
from datetime import datetime, timedelta, timezone
from sqlmodel import Session, select
from models import Vote
from database import engine
from socket_manager import broadcast_vote_end


async def auto_close_expired_votes():
    """定期检查并关闭过期的投票"""
    while True:
        try:
            with Session(engine) as session:
                # 查找所有 active 状态的投票
                stmt = select(Vote).where(Vote.status == "active")
                active_votes = session.exec(stmt).all()

                now = datetime.now(timezone.utc)

                for vote in active_votes:
                    if vote.started_at:
                        # 兼容 naive datetime：如果 started_at 无时区信息，当作 UTC 处理
                        started = vote.started_at
                        if started.tzinfo is None:
                            started = started.replace(tzinfo=timezone.utc)

                        # 计算投票结束时间
                        end_time = started + timedelta(seconds=vote.duration_seconds)

                        # 如果已到期，关闭投票
                        if now >= end_time:
                            print(f"[AUTO-CLOSE] Closing expired vote {vote.id} ('{vote.title}')")
                            vote.status = "closed"
                            session.add(vote)
                            session.commit()

                            # 计算结果并广播
                            try:
                                from routes.vote import _calculate_vote_result
                                final_results = _calculate_vote_result(vote.id, session)
                                await broadcast_vote_end(vote.meeting_id, vote.id, final_results)
                            except Exception as e:
                                print(f"[AUTO-CLOSE] Failed to broadcast vote end: {e}")

        except Exception as e:
            print(f"[AUTO-CLOSE] Error in auto_close_expired_votes: {e}")

        # 每10秒检查一次
        await asyncio.sleep(10)
