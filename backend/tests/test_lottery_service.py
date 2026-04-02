import sys
import unittest
from datetime import datetime
from pathlib import Path

from sqlmodel import SQLModel, Session, create_engine, select


WORKSPACE_DIR = Path(__file__).resolve().parents[2]
BACKEND_DIR = Path(__file__).resolve().parents[1]
if str(WORKSPACE_DIR) not in sys.path:
    sys.path.insert(0, str(WORKSPACE_DIR))
if str(BACKEND_DIR) not in sys.path:
    sys.path.insert(0, str(BACKEND_DIR))

import database as database_module  # noqa: E402
import models as models_module  # noqa: E402

sys.modules.setdefault("backend.database", database_module)
sys.modules.setdefault("backend.models", models_module)

from models import Lottery, LotteryParticipant, LotterySession, LotteryWinner, Meeting, User  # noqa: E402
from services.lottery_service import (  # noqa: E402
    LOTTERY_SESSION_READY,
    build_session_snapshot,
    recalculate_participant_winner_flags,
)


class LotteryServiceTestCase(unittest.TestCase):
    def setUp(self):
        self.engine = create_engine("sqlite://", connect_args={"check_same_thread": False})
        SQLModel.metadata.create_all(self.engine)

    def test_build_session_snapshot_includes_next_round_and_sorted_rounds(self):
        with Session(self.engine) as session:
            meeting = self._create_meeting(session)
            user = User(name="张三", department="办公室")
            session.add(user)
            session.commit()
            session.refresh(user)

            first_round = Lottery(
                meeting_id=meeting.id,
                title="第一轮",
                count=1,
                allow_repeat=False,
                sort_order=1,
                status="ready",
            )
            second_round = Lottery(
                meeting_id=meeting.id,
                title="第二轮",
                count=2,
                allow_repeat=True,
                sort_order=2,
                status="draft",
            )
            session.add(first_round)
            session.add(second_round)
            session.commit()
            session.refresh(first_round)
            session.refresh(second_round)

            lottery_session = LotterySession(
                meeting_id=meeting.id,
                session_status=LOTTERY_SESSION_READY,
                current_round_id=first_round.id,
            )
            session.add(lottery_session)
            session.add(
                LotteryParticipant(
                    meeting_id=meeting.id,
                    user_id=user.id,
                    user_name=user.name,
                    department=user.department,
                    status="joined",
                )
            )
            session.commit()

            snapshot = build_session_snapshot(meeting.id, session, user_id=user.id)

        self.assertEqual(first_round.id, snapshot["current_round_id"])
        self.assertEqual("第一轮", snapshot["current_round"]["title"])
        self.assertEqual(1, snapshot["current_round"]["sort_order"])
        self.assertEqual(second_round.id, snapshot["next_round_id"])
        self.assertEqual("第二轮", snapshot["next_round"]["title"])
        self.assertEqual([first_round.id, second_round.id], [item["id"] for item in snapshot["rounds"]])
        self.assertTrue(snapshot["joined"])
        self.assertTrue(snapshot["self_service_open"])

    def test_build_session_snapshot_returns_closed_self_service_after_roll_started(self):
        with Session(self.engine) as session:
            meeting = self._create_meeting(session)
            round_item = Lottery(
                meeting_id=meeting.id,
                title="第一轮",
                count=1,
                allow_repeat=False,
                sort_order=1,
                status="finished",
            )
            session.add(round_item)
            session.commit()
            session.refresh(round_item)

            lottery_session = LotterySession(
                meeting_id=meeting.id,
                session_status="result",
                current_round_id=round_item.id,
                self_service_locked=True,
            )
            session.add(lottery_session)
            session.commit()

            snapshot = build_session_snapshot(meeting.id, session)

        self.assertFalse(snapshot["self_service_open"])
        self.assertEqual("result", snapshot["session_status"])

    def test_recalculate_participant_winner_flags_clears_removed_winner_round(self):
        with Session(self.engine) as session:
            meeting = self._create_meeting(session)
            user = User(name="李四")
            session.add(user)
            session.commit()
            session.refresh(user)

            round_item = Lottery(
                meeting_id=meeting.id,
                title="主席台抽签",
                count=1,
                allow_repeat=False,
                sort_order=1,
                status="finished",
            )
            session.add(round_item)
            session.commit()
            session.refresh(round_item)

            participant = LotteryParticipant(
                meeting_id=meeting.id,
                user_id=user.id,
                user_name=user.name,
                status="joined",
                is_winner=True,
                winning_lottery_id=round_item.id,
            )
            session.add(participant)
            session.add(LotteryWinner(lottery_id=round_item.id, user_id=user.id, user_name=user.name))
            session.commit()

            winner = session.exec(select(LotteryWinner).where(LotteryWinner.lottery_id == round_item.id)).first()
            session.delete(winner)
            session.commit()

            dirty = recalculate_participant_winner_flags(meeting.id, session, rounds=[round_item])
            if dirty:
                session.commit()
            refreshed_participant = session.get(LotteryParticipant, (meeting.id, user.id))

        self.assertTrue(dirty)
        self.assertFalse(refreshed_participant.is_winner)
        self.assertIsNone(refreshed_participant.winning_lottery_id)

    def _create_meeting(self, session: Session) -> Meeting:
        meeting = Meeting(
            title="抽签测试会",
            start_time=datetime(2026, 4, 2, 9, 0, 0),
            end_time=datetime(2026, 4, 2, 10, 0, 0),
        )
        session.add(meeting)
        session.commit()
        session.refresh(meeting)
        return meeting


if __name__ == "__main__":
    unittest.main()
