import sys
import unittest
from datetime import datetime, timedelta
from pathlib import Path

from sqlmodel import SQLModel, Session, create_engine


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

from models import Meeting, User, UserVote, Vote, VoteOption  # noqa: E402
from routes.vote import (  # noqa: E402
    _build_public_vote_snapshot,
    _resolve_effective_vote_state,
    get_vote_history,
)


class VoteRuntimeTestCase(unittest.TestCase):
    def setUp(self):
        self.engine = create_engine("sqlite://", connect_args={"check_same_thread": False})
        SQLModel.metadata.create_all(self.engine)

    def test_runtime_state_resolution_does_not_mutate_vote_row(self):
        vote = Vote(
            meeting_id=1,
            title="预算表决",
            is_multiple=False,
            is_anonymous=False,
            max_selections=1,
            duration_seconds=60,
            countdown_seconds=10,
            status="active",
            started_at=datetime.now() - timedelta(minutes=2),
        )

        status, _, closed_at = _resolve_effective_vote_state(vote)

        self.assertEqual("closed", status)
        self.assertIsNotNone(closed_at)
        self.assertEqual("active", vote.status)
        self.assertIsNone(vote.closed_at)

    def test_public_snapshot_clears_user_specific_vote_fields(self):
        with Session(self.engine) as session:
            meeting = self._create_meeting(session)
            user = User(name="张三")
            session.add(user)
            session.commit()
            session.refresh(user)

            vote = self._create_vote(session, meeting.id, "实名投票", created_at=datetime(2026, 4, 2, 9, 0, 0))
            option_ids = self._create_options(session, vote.id, ["同意", "反对"])
            session.add(UserVote(vote_id=vote.id, user_id=user.id, option_id=option_ids[0], voted_at=datetime(2026, 4, 2, 9, 5, 0)))
            session.commit()

            snapshot = _build_public_vote_snapshot(vote, session)

        self.assertFalse(snapshot["user_voted"])
        self.assertEqual([], snapshot["selected_option_ids"])

    def test_vote_history_uses_latest_vote_time_for_stable_paging(self):
        with Session(self.engine) as session:
            meeting = self._create_meeting(session)
            user = User(name="李四")
            session.add(user)
            session.commit()
            session.refresh(user)

            oldest_vote = self._create_vote(session, meeting.id, "旧票", created_at=datetime(2026, 4, 1, 8, 0, 0))
            newest_vote = self._create_vote(session, meeting.id, "新票", created_at=datetime(2026, 4, 2, 8, 0, 0))
            middle_vote = self._create_vote(session, meeting.id, "中票", created_at=datetime(2026, 4, 1, 12, 0, 0))

            oldest_option = self._create_options(session, oldest_vote.id, ["A", "B"])[0]
            newest_option = self._create_options(session, newest_vote.id, ["A", "B"])[0]
            middle_option = self._create_options(session, middle_vote.id, ["A", "B"])[0]

            session.add(UserVote(vote_id=oldest_vote.id, user_id=user.id, option_id=oldest_option, voted_at=datetime(2026, 4, 2, 11, 0, 0)))
            session.add(UserVote(vote_id=newest_vote.id, user_id=user.id, option_id=newest_option, voted_at=datetime(2026, 4, 2, 12, 0, 0)))
            session.add(UserVote(vote_id=middle_vote.id, user_id=user.id, option_id=middle_option, voted_at=datetime(2026, 4, 2, 10, 0, 0)))
            session.commit()

            history = get_vote_history(user_id=user.id, skip=0, limit=2, session=session)

        self.assertEqual([newest_vote.id, oldest_vote.id], [item.id for item in history])

    def _create_meeting(self, session: Session) -> Meeting:
        meeting = Meeting(
            title="专题会",
            start_time=datetime(2026, 4, 2, 9, 0, 0),
            end_time=datetime(2026, 4, 2, 10, 0, 0),
        )
        session.add(meeting)
        session.commit()
        session.refresh(meeting)
        return meeting

    def _create_vote(self, session: Session, meeting_id: int, title: str, created_at: datetime) -> Vote:
        vote = Vote(
            meeting_id=meeting_id,
            title=title,
            is_multiple=False,
            is_anonymous=False,
            max_selections=1,
            duration_seconds=60,
            countdown_seconds=10,
            status="closed",
            created_at=created_at,
        )
        session.add(vote)
        session.commit()
        session.refresh(vote)
        return vote

    def _create_options(self, session: Session, vote_id: int, contents: list[str]) -> list[int]:
        option_ids = []
        for index, content in enumerate(contents):
            option = VoteOption(vote_id=vote_id, content=content, sort_order=index)
            session.add(option)
            session.commit()
            session.refresh(option)
            option_ids.append(option.id)
        return option_ids


if __name__ == "__main__":
    unittest.main()
