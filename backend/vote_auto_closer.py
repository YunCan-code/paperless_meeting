"""
自动检测并关闭过期投票的后台任务
"""
import asyncio
from datetime import datetime, timedelta, timezone
from sqlmodel import Session, select
from models import Vote
from database import engine
from socket_manager import broadcast_vote_results, broadcast_vote_state


async def auto_close_expired_votes():
    """定期检查并关闭过期的投票"""
    while True:
        try:
            with Session(engine) as session:
                stmt = select(Vote).where(Vote.status.in_(["countdown", "active"]))
                runtime_votes = session.exec(stmt).all()
                now = datetime.now(timezone.utc)

                for vote in runtime_votes:
                    if not vote.started_at:
                        continue

                    started = vote.started_at
                    if started.tzinfo is None:
                        started = started.replace(tzinfo=timezone.utc)

                    if vote.status == "countdown" and now >= started:
                        vote.status = "active"
                        session.add(vote)
                        session.commit()
                        session.refresh(vote)
                        try:
                            from routes.vote import _build_vote_read, _build_vote_result
                            snapshot = _build_vote_read(vote, session).model_dump()
                            snapshot["results"] = _build_vote_result(vote, session).model_dump()["results"]
                            await broadcast_vote_state(vote.meeting_id, snapshot)
                            await broadcast_vote_results(vote.meeting_id, vote.id, _build_vote_result(vote, session).model_dump())
                        except Exception as e:
                            print(f"[AUTO-CLOSE] Failed to broadcast vote activation: {e}")
                        continue

                    if vote.status == "active":
                        end_time = started + timedelta(seconds=vote.duration_seconds)
                        if now >= end_time:
                            print(f"[AUTO-CLOSE] Closing expired vote {vote.id} ('{vote.title}')")
                            vote.status = "closed"
                            vote.closed_at = now
                            session.add(vote)
                            session.commit()
                            session.refresh(vote)

                            try:
                                from routes.vote import _build_vote_read, _build_vote_result
                                snapshot = _build_vote_read(vote, session).model_dump()
                                snapshot["results"] = _build_vote_result(vote, session).model_dump()["results"]
                                await broadcast_vote_state(vote.meeting_id, snapshot)
                                await broadcast_vote_results(vote.meeting_id, vote.id, _build_vote_result(vote, session).model_dump())
                            except Exception as e:
                                print(f"[AUTO-CLOSE] Failed to broadcast vote end: {e}")

        except Exception as e:
            print(f"[AUTO-CLOSE] Error in auto_close_expired_votes: {e}")

        # 每10秒检查一次
        await asyncio.sleep(10)
