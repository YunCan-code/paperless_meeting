from fastapi import APIRouter, Depends
from sqlmodel import Session, select
from sqlalchemy import and_, func, extract
from datetime import datetime, timedelta
from typing import Optional
from zoneinfo import ZoneInfo

from database import get_session
from models import CheckIn, Meeting, MeetingType, ReadingProgress, User

router = APIRouter(prefix="/dashboard", tags=["数据看板"])

CST = ZoneInfo("Asia/Shanghai")


def _get_time_range(range_type: str):
    """根据 week/month/year 返回 (start_dt, end_dt)"""
    now = datetime.now(CST)
    if range_type == "week":
        start = (now - timedelta(days=now.weekday())).replace(hour=0, minute=0, second=0, microsecond=0)
    elif range_type == "month":
        start = now.replace(day=1, hour=0, minute=0, second=0, microsecond=0)
    elif range_type == "year":
        start = now.replace(month=1, day=1, hour=0, minute=0, second=0, microsecond=0)
    else:
        start = now.replace(month=1, day=1, hour=0, minute=0, second=0, microsecond=0)
    return start, now


@router.get("/stats/{user_id}")
def get_stats(user_id: int, range: str = "month", session: Session = Depends(get_session)):
    """
    统计数据：参会总数、签到数、参与类型数、阅读文件数
    range: week / month / year
    """
    start_dt, end_dt = _get_time_range(range)

    # 参会总数 = 该用户在时间范围内签到过的会议数
    checkin_count = session.exec(
        select(func.count(CheckIn.id)).where(
            and_(CheckIn.user_id == user_id,
                 CheckIn.check_in_time >= start_dt,
                 CheckIn.check_in_time <= end_dt)
        )
    ).one()

    # 签到数（非补签）
    direct_checkin_count = session.exec(
        select(func.count(CheckIn.id)).where(
            and_(CheckIn.user_id == user_id,
                 CheckIn.is_makeup == False,
                 CheckIn.check_in_time >= start_dt,
                 CheckIn.check_in_time <= end_dt)
        )
    ).one()

    # 参与类型数：该用户签到过的会议涉及多少种类型
    type_count = session.exec(
        select(func.count(func.distinct(Meeting.meeting_type_id))).where(
            and_(
                Meeting.id == CheckIn.meeting_id,
                CheckIn.user_id == user_id,
                CheckIn.check_in_time >= start_dt,
                CheckIn.check_in_time <= end_dt,
                Meeting.meeting_type_id != None,
            )
        )
    ).one()

    # 阅读文件数
    reading_count = session.exec(
        select(func.count(ReadingProgress.id)).where(
            and_(ReadingProgress.user_id == user_id,
                 ReadingProgress.updated_at >= start_dt,
                 ReadingProgress.updated_at <= end_dt)
        )
    ).one()

    # 累计会议时长（用户自己记录的 duration_minutes 之和）
    total_duration = session.exec(
        select(func.coalesce(func.sum(CheckIn.duration_minutes), 0)).where(
            and_(CheckIn.user_id == user_id,
                 CheckIn.check_in_time >= start_dt,
                 CheckIn.check_in_time <= end_dt)
        )
    ).one()

    return {
        "meeting_count": checkin_count or 0,
        "checkin_count": direct_checkin_count or 0,
        "type_count": type_count or 0,
        "reading_count": reading_count or 0,
        "total_duration_minutes": total_duration or 0,
        "range": range,
    }


@router.get("/heatmap/{user_id}")
def get_heatmap(user_id: int, session: Session = Depends(get_session)):
    """
    时段热力图：返回每个 (星期几, 小时) 的会议数量
    基于用户签到过的会议的 start_time
    """
    checkins = session.exec(
        select(CheckIn, Meeting).where(
            and_(CheckIn.user_id == user_id, CheckIn.meeting_id == Meeting.id)
        )
    ).all()

    heatmap = {}  # key: "dow_hour", value: count
    for checkin, meeting in checkins:
        st = meeting.start_time
        dow = st.weekday()  # 0=Monday
        hour = st.hour
        key = f"{dow}_{hour}"
        heatmap[key] = heatmap.get(key, 0) + 1

    return {"heatmap": heatmap}


@router.get("/collaborators/{user_id}")
def get_collaborators(user_id: int, session: Session = Depends(get_session)):
    """
    协作关系 Top 5：通过打卡记录推断共同参会者
    """
    from sqlalchemy import text
    result = session.exec(
        text("""
            SELECT c2.user_id, u.name, COUNT(*) as co_meetings
            FROM checkin c1
            JOIN checkin c2 ON c1.meeting_id = c2.meeting_id AND c1.user_id != c2.user_id
            JOIN "user" u ON c2.user_id = u.id
            WHERE c1.user_id = :uid
            GROUP BY c2.user_id, u.name
            ORDER BY co_meetings DESC
            LIMIT 5
        """).bindparams(uid=user_id)
    ).all()

    return {
        "collaborators": [
            {"user_id": row[0], "name": row[1], "co_meetings": row[2]}
            for row in result
        ]
    }


@router.get("/type-distribution/{user_id}")
def get_type_distribution(user_id: int, range: str = "year", session: Session = Depends(get_session)):
    """
    会议类型分布
    """
    start_dt, end_dt = _get_time_range(range)

    from sqlalchemy import text
    result = session.exec(
        text("""
            SELECT mt.name, COUNT(*) as count
            FROM checkin c
            JOIN meeting m ON c.meeting_id = m.id
            LEFT JOIN meetingtype mt ON m.meeting_type_id = mt.id
            WHERE c.user_id = :uid
              AND c.check_in_time >= :start
              AND c.check_in_time <= :end_dt
            GROUP BY mt.name
            ORDER BY count DESC
        """).bindparams(uid=user_id, start=start_dt, end_dt=end_dt)
    ).all()

    return {
        "distribution": [
            {"type_name": row[0] or "未分类", "count": row[1]}
            for row in result
        ]
    }


@router.get("/checkin-history/{user_id}")
def get_checkin_history(user_id: int, skip: int = 0, limit: int = 20, session: Session = Depends(get_session)):
    """
    签到历史记录（分页）
    """
    checkins = session.exec(
        select(CheckIn, Meeting).where(
            and_(CheckIn.user_id == user_id, CheckIn.meeting_id == Meeting.id)
        ).order_by(CheckIn.check_in_time.desc()).offset(skip).limit(limit)
    ).all()

    return [
        {
            "id": c.id,
            "meeting_id": m.id,
            "meeting_title": m.title,
            "check_in_time": c.check_in_time.isoformat(),
            "duration_minutes": c.duration_minutes,
            "is_makeup": c.is_makeup,
            "remark": c.remark,
        }
        for c, m in checkins
    ]
