"""
自动检测并关闭过期投票的后台任务
"""
import asyncio
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
                from routes.vote import _build_public_vote_snapshot, _build_vote_result, _resolve_effective_vote_state

                for vote in runtime_votes:
                    effective_status, _, effective_closed_at = _resolve_effective_vote_state(vote)
                    if effective_status == vote.status and effective_closed_at == vote.closed_at:
                        continue

                    previous_status = vote.status
                    vote.status = effective_status
                    vote.closed_at = effective_closed_at
                    session.add(vote)
                    session.commit()
                    session.refresh(vote)

                    try:
                        result_payload = _build_vote_result(vote, session).model_dump(mode="json")
                        snapshot = _build_public_vote_snapshot(vote, session)
                        snapshot["results"] = result_payload["results"]
                        await broadcast_vote_state(vote.meeting_id, snapshot)
                        await broadcast_vote_results(vote.meeting_id, vote.id, result_payload)
                    except Exception as e:
                        print(f"[AUTO-CLOSE] Failed to broadcast vote state change: {e}")

                    if previous_status == "countdown" and effective_status == "active":
                        print(f"[AUTO-CLOSE] Activated vote {vote.id} ('{vote.title}')")
                    elif effective_status == "closed":
                        print(f"[AUTO-CLOSE] Closing expired vote {vote.id} ('{vote.title}')")

        except Exception as e:
            print(f"[AUTO-CLOSE] Error in auto_close_expired_votes: {e}")

        # 每10秒检查一次
        await asyncio.sleep(10)
