from fastapi import APIRouter, Depends, HTTPException, UploadFile, File, Form, Request
from sqlmodel import Session, select, SQLModel, delete
from sqlalchemy import func
from typing import List, Optional
import shutil
from pathlib import Path
from datetime import datetime, timedelta
from urllib.parse import urlparse, parse_qsl, urlencode, urlunparse
import json
import hashlib
from zoneinfo import ZoneInfo

from database import get_session
from models import (
    Meeting,
    MeetingType,
    Attachment,
    AttachmentRead,
    Vote,
    VoteOption,
    UserVote,
    MeetingAttendeeLink,
    User,
    CheckIn,
    SystemSetting,
)
from socket_manager import sio, broadcast_meeting_changed

from pydantic import BaseModel

class AttendeeRoleInput(BaseModel):
    user_id: int
    meeting_role: str = "参会人员"

class AttendeeEntryInput(BaseModel):
    type: str = "user"
    user_id: Optional[int] = None
    name: Optional[str] = None
    meeting_role: str = "参会人员"

class AgendaItemInput(BaseModel):
    content: str

class MeetingContactInput(BaseModel):
    name: str
    short_phone: Optional[str] = None
    phone: Optional[str] = None
    email: Optional[str] = None

class MeetingCreateInput(BaseModel):
    title: str
    meeting_type_id: Optional[int] = None
    cover_image: Optional[str] = None
    start_time: datetime
    end_time: Optional[datetime] = None
    location: Optional[str] = None
    speaker: Optional[str] = None
    agenda: Optional[str] = None
    status: str = "scheduled"
    attendees_roles: Optional[List[AttendeeRoleInput]] = None
    attendee_entries: Optional[List[AttendeeEntryInput]] = None
    agenda_items: Optional[List[AgendaItemInput]] = None
    meeting_contacts: Optional[List[MeetingContactInput]] = None
    show_media_link: bool = False
    android_visibility_mode: str = "inherit"
    android_visibility_hide_after_hours: Optional[int] = None

class MeetingUpdateInput(BaseModel):
    title: Optional[str] = None
    meeting_type_id: Optional[int] = None
    cover_image: Optional[str] = None
    start_time: Optional[datetime] = None
    end_time: Optional[datetime] = None
    location: Optional[str] = None
    speaker: Optional[str] = None
    agenda: Optional[str] = None
    status: Optional[str] = None
    attendees_roles: Optional[List[AttendeeRoleInput]] = None
    attendee_entries: Optional[List[AttendeeEntryInput]] = None
    agenda_items: Optional[List[AgendaItemInput]] = None
    meeting_contacts: Optional[List[MeetingContactInput]] = None
    show_media_link: Optional[bool] = None
    android_visibility_mode: Optional[str] = None
    android_visibility_hide_after_hours: Optional[int] = None

class AgendaItemOutput(BaseModel):
    content: str

class MeetingContactOutput(BaseModel):
    name: str
    short_phone: Optional[str] = None
    phone: Optional[str] = None
    email: Optional[str] = None

class AttendeeOutput(BaseModel):
    type: str = "user"
    user_id: Optional[int] = None
    name: str
    meeting_role: str = "参会人员"

# 创建路由器，前缀为 /meetings
router = APIRouter(prefix="/meetings", tags=["meetings"])

# 定义上传文件存储目录
UPLOAD_DIR = Path("uploads")
# 确保目录存在
UPLOAD_DIR.mkdir(parents=True, exist_ok=True)

# 文件上传安全限制
ALLOWED_EXTENSIONS = {'.pdf'}  # 仅允许 PDF 文件（与前端一致）
MAX_UPLOAD_SIZE = 200 * 1024 * 1024  # 200MB
MEETING_COVER_UPLOAD_DIR = UPLOAD_DIR / "meeting_covers"
MEETING_COVER_UPLOAD_DIR.mkdir(parents=True, exist_ok=True)
ALLOWED_IMAGE_EXTENSIONS = {".jpg", ".jpeg", ".png", ".webp"}
MAX_IMAGE_UPLOAD_SIZE = 5 * 1024 * 1024
STALE_MEETING_COVER_TTL_SECONDS = 24 * 60 * 60

MEETING_BASE_FIELDS = {
    "title",
    "meeting_type_id",
    "cover_image",
    "start_time",
    "end_time",
    "location",
    "speaker",
    "agenda",
    "status",
    "show_media_link",
    "android_visibility_mode",
    "android_visibility_hide_after_hours",
}

def _parse_datetime_value(value):
    if isinstance(value, str):
        try:
            return datetime.fromisoformat(value.replace('Z', '+00:00'))
        except ValueError:
            return value
    return value

def _load_json_list(raw_value: Optional[str]) -> List:
    if not raw_value:
        return []
    try:
        parsed = json.loads(raw_value)
        return parsed if isinstance(parsed, list) else []
    except Exception:
        return []

def _normalize_agenda_items(
    agenda_items: Optional[List[AgendaItemInput]] = None,
    agenda_raw: Optional[str] = None
) -> List[dict]:
    if agenda_items is not None:
        normalized = []
        for item in agenda_items:
            content = (item.content or "").strip()
            if content:
                normalized.append({"content": content})
        return normalized

    normalized = []
    for item in _load_json_list(agenda_raw):
        if isinstance(item, dict):
            content = str(item.get("content") or "").strip()
            if content:
                normalized.append({"content": content})
        elif isinstance(item, str):
            content = item.strip()
            if content:
                normalized.append({"content": content})
    return normalized

def _serialize_agenda_items(
    agenda_items: Optional[List[AgendaItemInput]] = None,
    agenda_raw: Optional[str] = None
) -> Optional[str]:
    normalized = _normalize_agenda_items(agenda_items, agenda_raw)
    if not normalized:
        return None
    return json.dumps(normalized, ensure_ascii=False)

def _normalize_meeting_contacts(
    contacts: Optional[List[MeetingContactInput]]
) -> List[dict]:
    if contacts is None:
        return []

    normalized = []
    for contact in contacts:
        name = (contact.name or "").strip()
        if not name:
            continue
        normalized.append({
            "name": name,
            "short_phone": (contact.short_phone or "").strip() or None,
            "phone": (contact.phone or "").strip() or None,
            "email": (contact.email or "").strip() or None,
        })
    return normalized

def _serialize_meeting_contacts(contacts: Optional[List[MeetingContactInput]]) -> Optional[str]:
    normalized = _normalize_meeting_contacts(contacts)
    if not normalized:
        return None
    return json.dumps(normalized, ensure_ascii=False)

def _parse_meeting_contacts(raw_value: Optional[str]) -> List[MeetingContactOutput]:
    return [
        MeetingContactOutput(
            name=str(item.get("name") or "").strip(),
            short_phone=item.get("short_phone"),
            phone=item.get("phone"),
            email=item.get("email")
        )
        for item in _load_json_list(raw_value)
        if isinstance(item, dict) and str(item.get("name") or "").strip()
    ]

def _split_attendees_payload(
    attendee_entries: Optional[List[AttendeeEntryInput]] = None,
    attendees_roles: Optional[List[AttendeeRoleInput]] = None
):
    user_entries = []
    manual_entries = []

    if attendee_entries is not None:
        for entry in attendee_entries:
            role = (entry.meeting_role or "参会人员").strip() or "参会人员"
            if entry.type == "manual":
                name = (entry.name or "").strip()
                if name:
                    manual_entries.append({"name": name, "meeting_role": role})
            else:
                if entry.user_id is not None:
                    user_entries.append({"user_id": entry.user_id, "meeting_role": role})
        return user_entries, manual_entries

    if attendees_roles is not None:
        for attendee in attendees_roles:
            user_entries.append({
                "user_id": attendee.user_id,
                "meeting_role": (attendee.meeting_role or "参会人员").strip() or "参会人员"
            })

    return user_entries, manual_entries

def _serialize_manual_attendees(manual_entries: List[dict]) -> Optional[str]:
    if not manual_entries:
        return None
    return json.dumps(manual_entries, ensure_ascii=False)

def _parse_manual_attendees(raw_value: Optional[str]) -> List[AttendeeOutput]:
    return [
        AttendeeOutput(
            type="manual",
            user_id=None,
            name=str(item.get("name") or "").strip(),
            meeting_role=str(item.get("meeting_role") or "参会人员").strip() or "参会人员"
        )
        for item in _load_json_list(raw_value)
        if isinstance(item, dict) and str(item.get("name") or "").strip()
    ]

VALID_ANDROID_VISIBILITY_MODES = {"inherit", "custom_hours", "hidden", "always_show"}
SHANGHAI_TZ = ZoneInfo("Asia/Shanghai")

def _normalize_android_visibility_mode(mode: Optional[str]) -> str:
    normalized = (mode or "inherit").strip().lower()
    if normalized not in VALID_ANDROID_VISIBILITY_MODES:
        raise HTTPException(status_code=400, detail="Invalid android_visibility_mode")
    return normalized

def _normalize_android_visibility_hours(hours: Optional[int]) -> Optional[int]:
    if hours is None:
        return None
    if hours < 0:
        raise HTTPException(status_code=400, detail="android_visibility_hide_after_hours cannot be negative")
    return hours

def _apply_android_visibility_defaults(payload: dict) -> dict:
    if "android_visibility_mode" in payload:
        payload["android_visibility_mode"] = _normalize_android_visibility_mode(payload.get("android_visibility_mode"))
    else:
        payload["android_visibility_mode"] = "inherit"

    if "android_visibility_hide_after_hours" in payload:
        payload["android_visibility_hide_after_hours"] = _normalize_android_visibility_hours(
            payload.get("android_visibility_hide_after_hours")
        )
    else:
        payload["android_visibility_hide_after_hours"] = None

    if payload["android_visibility_mode"] == "custom_hours" and payload["android_visibility_hide_after_hours"] is None:
        payload["android_visibility_hide_after_hours"] = 0
    elif payload["android_visibility_mode"] != "custom_hours":
        payload["android_visibility_hide_after_hours"] = None

    return payload

def _get_checkin_map_for_user(meetings: List[Meeting], user_id: Optional[int], session: Session) -> dict[int, CheckIn]:
    if not user_id or not meetings:
        return {}

    meeting_ids = [meeting.id for meeting in meetings if meeting.id is not None]
    if not meeting_ids:
        return {}

    checkins = session.exec(
        select(CheckIn).where(
            CheckIn.user_id == user_id,
            CheckIn.meeting_id.in_(meeting_ids)
        )
    ).all()
    return {checkin.meeting_id: checkin for checkin in checkins}


def _get_attending_meeting_ids_for_user(user_id: Optional[int], session: Session) -> set[int]:
    if not user_id:
        return set()

    return {
        link.meeting_id
        for link in session.exec(
            select(MeetingAttendeeLink).where(MeetingAttendeeLink.user_id == user_id)
        ).all()
        if link.meeting_id is not None
    }

def _resolve_effective_visibility_hours(meeting: Meeting, session: Session) -> Optional[int]:
    mode = (meeting.android_visibility_mode or "inherit").strip().lower()

    if mode == "always_show":
        return 0
    if mode == "hidden":
        return -1
    if mode == "custom_hours":
        return meeting.android_visibility_hide_after_hours or 0

    hide_after_hours_setting = session.get(SystemSetting, "meeting_visibility_hide_after_hours")
    if not hide_after_hours_setting or not hide_after_hours_setting.value:
        return 0

    try:
        return int(hide_after_hours_setting.value)
    except ValueError:
        return 0

def _is_meeting_visible_for_android(
    meeting: Meeting,
    session: Session,
    force_show_all: bool = False,
    checkin: Optional[CheckIn] = None
) -> bool:
    if force_show_all:
        return True

    if checkin is not None:
        return True

    effective_hours = _resolve_effective_visibility_hours(meeting, session)
    if effective_hours is None or effective_hours == 0:
        return True
    if effective_hours < 0:
        return False

    threshold = datetime.now() - timedelta(hours=effective_hours)
    return meeting.start_time > threshold

def _meeting_occurs_today_in_shanghai(meeting: Meeting) -> bool:
    start_time = meeting.start_time
    if start_time.tzinfo is not None:
        start_time = start_time.astimezone(SHANGHAI_TZ)
    return start_time.date() == datetime.now(SHANGHAI_TZ).date()


def _validate_image_upload(file: UploadFile) -> tuple[bytes, str, str]:
    original_filename = file.filename or "cover"
    safe_original = os.path.basename(original_filename).replace("..", "")
    extension = Path(safe_original).suffix.lower()

    if extension not in ALLOWED_IMAGE_EXTENSIONS:
        raise HTTPException(status_code=400, detail="仅支持 JPG、PNG、WebP 格式图片")

    content = file.file.read()
    if not content:
        raise HTTPException(status_code=400, detail="上传文件不能为空")
    if len(content) > MAX_IMAGE_UPLOAD_SIZE:
        raise HTTPException(status_code=400, detail="图片大小不能超过 5MB")

    stem = Path(safe_original).stem or "cover"
    return content, extension, stem


def _normalize_optional_image_url(image_url: Optional[str]) -> Optional[str]:
    normalized = (image_url or "").strip()
    return normalized or None


def _delete_meeting_cover_if_unused(
    session: Session,
    image_url: Optional[str],
    exclude_meeting_id: Optional[int] = None
) -> None:
    if not image_url:
        return

    statement = select(Meeting).where(Meeting.cover_image == image_url)
    if exclude_meeting_id is not None:
        statement = statement.where(Meeting.id != exclude_meeting_id)
    if session.exec(statement).first() is not None:
        return

    source = _resolve_local_source_path(image_url)
    try:
        if source and source.exists() and source.is_file():
            source.unlink()
    except OSError:
        pass


def _cleanup_stale_meeting_covers(session: Session, keep_urls: set[str] | None = None) -> None:
    keep = keep_urls or set()
    referenced = {
        cover for cover in session.exec(select(Meeting.cover_image)).all()
        if cover
    }
    cutoff = datetime.now().timestamp() - STALE_MEETING_COVER_TTL_SECONDS

    for file in MEETING_COVER_UPLOAD_DIR.glob("*"):
        if not file.is_file():
            continue
        cover_url = f"/static/meeting_covers/{file.name}"
        if cover_url in keep or cover_url in referenced:
            continue
        try:
            if file.stat().st_mtime >= cutoff:
                continue
            file.unlink()
        except OSError:
            pass

@router.post("/", response_model=Meeting)
def create_meeting(meeting_in: MeetingCreateInput, session: Session = Depends(get_session)):
    """
    创建新会议
    """
    payload = {
        key: _parse_datetime_value(value) if key in {"start_time", "end_time"} else value
        for key, value in meeting_in.model_dump().items()
        if key in MEETING_BASE_FIELDS
    }
    payload = _apply_android_visibility_defaults(payload)
    payload["cover_image"] = _normalize_optional_image_url(payload.get("cover_image"))
    payload["agenda"] = _serialize_agenda_items(meeting_in.agenda_items, meeting_in.agenda)
    user_entries, manual_entries = _split_attendees_payload(
        attendee_entries=meeting_in.attendee_entries,
        attendees_roles=meeting_in.attendees_roles
    )
    payload["manual_attendees"] = _serialize_manual_attendees(manual_entries)
    payload["meeting_contacts"] = _serialize_meeting_contacts(meeting_in.meeting_contacts)

    meeting = Meeting(**payload)
    session.add(meeting)
    session.commit()
    session.refresh(meeting)
    
    # Handle Attendees and Roles
    for attendee in user_entries:
        link = MeetingAttendeeLink(
            meeting_id=meeting.id,
            user_id=attendee["user_id"],
            meeting_role=attendee["meeting_role"]
        )
        session.add(link)
    if user_entries:
        session.commit()

    try:
        sio.start_background_task(
            broadcast_meeting_changed,
            "created",
            {
                "meeting_id": meeting.id,
                "title": meeting.title,
                "start_time": meeting.start_time.isoformat() if meeting.start_time else None
            }
        )
    except Exception as e:
        print(f"[Socket.IO] failed to broadcast meeting_changed: {e}")
    
    return meeting


@router.post("/upload_cover")
def upload_cover(file: UploadFile = File(...), session: Session = Depends(get_session)):
    content, extension, stem = _validate_image_upload(file)
    version = str(int(datetime.now().timestamp()))
    safe_stem = "".join(ch if ch.isalnum() or ch in {"-", "_"} else "_" for ch in stem).strip("_") or "cover"
    filename = f"{safe_stem}_{version}{extension}"
    file_path = MEETING_COVER_UPLOAD_DIR / filename

    with open(file_path, "wb") as output:
        output.write(content)

    cover_url = f"/static/meeting_covers/{filename}"
    _cleanup_stale_meeting_covers(session, keep_urls={cover_url})

    return {
        "url": cover_url,
        "version": version
    }


@router.get("/stats")
def get_meeting_stats(session: Session = Depends(get_session)):
    """
    获取会议统计数据 (本年/本月/本周/存储) + 环比数据
    (使用中国标准时间 CST UTC+8 计算边界)
    """
    from datetime import timezone
    
    # 1. Define CST Timezone
    cst_tz = timezone(timedelta(hours=8))
    
    # Get Now in CST
    now_cst = datetime.now(cst_tz)
    
    # Helper to count in range (Input DB assumes UTC)
    def count_in_range(start_dt_cst, end_dt_cst):
        if not start_dt_cst or not end_dt_cst: return 0
        
        # Convert CST bounds to UTC for DB Query
        start_utc = start_dt_cst.astimezone(timezone.utc)
        end_utc = end_dt_cst.astimezone(timezone.utc)
        
        return session.exec(
            select(func.count(Meeting.id))
            .where(Meeting.start_time >= start_utc)
            .where(Meeting.start_time < end_utc)
        ).one()

    # --- 1. Annual (Yearly) ---
    # Current Year (CST)
    current_year_start = datetime(now_cst.year, 1, 1, tzinfo=cst_tz)
    current_year_end = datetime(now_cst.year + 1, 1, 1, tzinfo=cst_tz)
    yearly_count = count_in_range(current_year_start, current_year_end)
    
    # Previous Year (YoY)
    last_year_start = datetime(now_cst.year - 1, 1, 1, tzinfo=cst_tz)
    last_year_end = datetime(now_cst.year, 1, 1, tzinfo=cst_tz)
    last_yearly_count = count_in_range(last_year_start, last_year_end)
    
    yearly_growth = 0.0
    if last_yearly_count > 0:
        yearly_growth = ((yearly_count - last_yearly_count) / last_yearly_count) * 100
    
    # --- 2. Monthly ---
    # Current Month (CST)
    current_month_start = datetime(now_cst.year, now_cst.month, 1, tzinfo=cst_tz)
    if now_cst.month == 12:
        current_month_end = datetime(now_cst.year + 1, 1, 1, tzinfo=cst_tz)
    else:
        current_month_end = datetime(now_cst.year, now_cst.month + 1, 1, tzinfo=cst_tz)
    monthly_count = count_in_range(current_month_start, current_month_end)
    
    # Previous Month (MoM)
    last_month_date = current_month_start - timedelta(days=1)
    last_month_start = datetime(last_month_date.year, last_month_date.month, 1, tzinfo=cst_tz)
    last_month_end = current_month_start
    last_monthly_count = count_in_range(last_month_start, last_month_end)
    
    monthly_growth = 0.0
    if last_monthly_count > 0:
        monthly_growth = ((monthly_count - last_monthly_count) / last_monthly_count) * 100
    
    # --- 3. Weekly ---
    # Current Week (CST)
    # weekday(): Mon=0, Sun=6
    start_of_week = now_cst - timedelta(days=now_cst.weekday())
    start_of_week = start_of_week.replace(hour=0, minute=0, second=0, microsecond=0)
    end_of_week = start_of_week + timedelta(days=7)
    
    weekly_count = count_in_range(start_of_week, end_of_week)
    
    # Previous Week
    start_of_last_week = start_of_week - timedelta(days=7)
    end_of_last_week = start_of_week
    last_weekly_count = count_in_range(start_of_last_week, end_of_last_week)
    
    weekly_growth = 0.0
    if last_weekly_count > 0:
        weekly_growth = ((weekly_count - last_weekly_count) / last_weekly_count) * 100
    
    # --- 4. Storage ---
    # Total now
    total_bytes = session.exec(select(func.sum(Attachment.file_size))).one() or 0
    
    # Total Month Ago
    # Use current_month_start (CST) -> UTC
    current_month_start_utc = current_month_start.astimezone(timezone.utc)
    
    total_bytes_start_of_month = session.exec(
        select(func.sum(Attachment.file_size))
        .where(Attachment.uploaded_at < current_month_start_utc)
    ).one() or 0
    
    storage_growth = 0.0
    if total_bytes_start_of_month > 0:
        storage_growth = ((total_bytes - total_bytes_start_of_month) / total_bytes_start_of_month) * 100
    elif total_bytes > 0:
        storage_growth = 100.0
        
    return {
        "yearly_count": yearly_count,
        "yearly_growth": round(yearly_growth, 1),
        
        "monthly_count": monthly_count,
        "monthly_growth": round(monthly_growth, 1),
        
        "weekly_count": weekly_count,
        "weekly_growth": round(weekly_growth, 1),
        
        "total_storage_bytes": total_bytes,
        "storage_growth": round(storage_growth, 1)
    }

import os

# Enhanced Response Model
class MeetingCardResponse(BaseModel):
    id: int
    title: str
    meeting_type_id: Optional[int] = None
    cover_image: Optional[str] = None
    start_time: datetime
    end_time: Optional[datetime] = None
    location: Optional[str] = None
    speaker: Optional[str] = None
    agenda: Optional[str] = None
    status: str = "scheduled"
    created_at: datetime
    card_image_url: Optional[str] = None
    card_image_thumb_url: Optional[str] = None
    card_image_source: Optional[str] = None
    meeting_type_name: Optional[str] = None
    attachments: List[AttachmentRead] = []
    agenda_items: List[AgendaItemOutput] = []
    meeting_contacts: List[MeetingContactOutput] = []
    show_media_link: bool = False
    android_visibility_mode: str = "inherit"
    android_visibility_hide_after_hours: Optional[int] = None
    is_checked_in: bool = False
    checkin_id: Optional[int] = None
    check_in_time: Optional[datetime] = None
    is_today_meeting: bool = False

# 默认会议图统一放到 uploads/meeting_defaults 下，通过现有 /static 链路暴露
DEFAULT_IMAGES = {
    "weekly": "/static/meeting_defaults/weekly.png",
    "urgent": "/static/meeting_defaults/urgent.png",
    "review": "/static/meeting_defaults/review.png",
    "kickoff": "/static/meeting_defaults/kickoff.png",
    "default": "/static/meeting_defaults/default.png",
}

from cachetools import TTLCache

# 带 TTL 的缓存：最多缓存 32 个目录，60 秒后自动失效
_image_dir_cache = TTLCache(maxsize=32, ttl=60)

THUMB_WIDTH = 960
THUMB_HEIGHT = 540
THUMB_QUALITY = 72
THUMB_DIR = UPLOAD_DIR / "thumbnails"
THUMB_DIR.mkdir(parents=True, exist_ok=True)

try:
    from PIL import Image, ImageOps  # type: ignore
    PIL_AVAILABLE = True
except Exception:
    PIL_AVAILABLE = False


def _optimize_unsplash_url(
    url: str,
    width: int = THUMB_WIDTH,
    height: int = THUMB_HEIGHT,
    quality: int = THUMB_QUALITY
) -> str:
    """Return a lower-cost Unsplash URL for list thumbnails."""
    try:
        parsed = urlparse(url)
        host = (parsed.hostname or "").lower()
        if "images.unsplash.com" not in host:
            return url

        existing = parse_qsl(parsed.query, keep_blank_values=True)
        filtered = [
            (k, v)
            for (k, v) in existing
            if k.lower() not in {"w", "h", "q", "fit", "auto"}
        ]
        filtered.extend(
            [
                ("auto", "format"),
                ("fit", "crop"),
                ("w", str(width)),
                ("h", str(height)),
                ("q", str(quality)),
            ]
        )
        return urlunparse(parsed._replace(query=urlencode(filtered)))
    except Exception:
        return url


def _resolve_local_source_path(image_url: str) -> Optional[Path]:
    """
    Resolve /static/... image URL to local file path under uploads/.
    Returns None for external URLs.
    """
    if not image_url:
        return None

    parsed = urlparse(image_url)
    path = parsed.path or image_url
    if not path.startswith("/static/"):
        return None

    rel = path.replace("/static/", "", 1)
    rel_path = Path(rel)
    if any(part == ".." for part in rel_path.parts):
        return None

    try:
        source = (UPLOAD_DIR / rel_path).resolve()
        upload_root = UPLOAD_DIR.resolve()
        if upload_root not in source.parents and source != upload_root:
            return None
        if not source.is_file():
            return None
        return source
    except Exception:
        return None


def _build_thumbnail_for_local_file(source_path: Path) -> Optional[Path]:
    """
    Build (or reuse) WebP thumbnail file for local image and return relative path under uploads/.
    """
    if not PIL_AVAILABLE:
        return None

    try:
        relative_source = source_path.resolve().relative_to(UPLOAD_DIR.resolve())
    except Exception:
        return None

    ext = source_path.suffix.lower().lstrip(".") or "img"
    thumb_name = f"{source_path.stem}_{ext}_{THUMB_WIDTH}x{THUMB_HEIGHT}.webp"
    thumb_relative = Path("thumbnails") / relative_source.parent / thumb_name
    thumb_path = (UPLOAD_DIR / thumb_relative).resolve()

    try:
        thumb_path.parent.mkdir(parents=True, exist_ok=True)

        source_mtime = source_path.stat().st_mtime
        if thumb_path.exists() and thumb_path.stat().st_mtime >= source_mtime:
            return thumb_relative

        resample = Image.Resampling.LANCZOS if hasattr(Image, "Resampling") else Image.LANCZOS
        with Image.open(source_path) as img:
            img = ImageOps.exif_transpose(img).convert("RGB")
            fitted = ImageOps.fit(img, (THUMB_WIDTH, THUMB_HEIGHT), method=resample)
            fitted.save(thumb_path, format="WEBP", quality=THUMB_QUALITY, method=6)

        return thumb_relative
    except Exception as e:
        print(f"[thumb] failed to build thumbnail for {source_path}: {e}")
        return None


def build_thumbnail_url(image_url: Optional[str], base_url: str) -> Optional[str]:
    """
    Create thumbnail URL for local static images; for Unsplash, return optimized URL.
    Fallback is original URL.
    """
    if not image_url:
        return None

    source = _resolve_local_source_path(image_url)
    if source is not None:
        thumb_relative = _build_thumbnail_for_local_file(source)
        if thumb_relative is not None:
            thumb_path = UPLOAD_DIR / thumb_relative
            return _append_version_query(
                f"{base_url}static/{thumb_relative.as_posix()}",
                _file_version_token(thumb_path)
            )
        return image_url

    return _optimize_unsplash_url(image_url)


def _append_version_query(url: Optional[str], version: Optional[str]) -> Optional[str]:
    if not url or not version:
        return url

    parsed = urlparse(url)
    query = parse_qsl(parsed.query, keep_blank_values=True)
    filtered = [(key, value) for key, value in query if key.lower() != "v"]
    filtered.append(("v", version))
    return urlunparse(parsed._replace(query=urlencode(filtered)))


def _file_version_token(path: Path) -> Optional[str]:
    try:
        return str(int(path.stat().st_mtime))
    except OSError:
        return None


def _public_static_url(relative_path: str, base_url: str, source_path: Optional[Path] = None) -> str:
    url = f"{base_url.rstrip('/')}{relative_path}"
    return _append_version_query(url, _file_version_token(source_path) if source_path else None)


def _normalize_public_image_url(image_url: Optional[str], base_url: str) -> Optional[str]:
    if not image_url:
        return None
    if image_url.startswith("/static/"):
        source = _resolve_local_source_path(image_url)
        return _public_static_url(image_url, base_url, source)
    return image_url


def _classify_default_source(type_name: str) -> str:
    if "周" in type_name or "例" in type_name:
        return "weekly"
    if "急" in type_name:
        return "urgent"
    if "评" in type_name or "审" in type_name:
        return "review"
    if "启" in type_name:
        return "kickoff"
    return "default"

def get_images_in_dir_cached(directory: Path) -> List[str]:
    """Cached directory listing to reduce Disk I/O (60s TTL)"""
    cache_key = str(directory)
    if cache_key in _image_dir_cache:
        return _image_dir_cache[cache_key]

    if not directory.exists() or not directory.is_dir():
        result = []
    else:
        valid_extensions = {'.jpg', '.jpeg', '.png', '.webp'}
        try:
            result = [
                f for f in os.listdir(directory)
                if os.path.isfile(directory / f) and os.path.splitext(f)[1].lower() in valid_extensions
            ]
            result.sort()
        except OSError:
            result = []

    _image_dir_cache[cache_key] = result
    return result

def _stable_image_index(seed: str, total: int) -> int:
    digest = hashlib.sha256(seed.encode("utf-8")).digest()
    return int.from_bytes(digest[:8], byteorder="big", signed=False) % total


def get_stable_image_from_dir(directory: Path, base_url: str, seed: str) -> Optional[str]:
    """Pick a stable image for the same meeting instead of re-randomizing every request."""
    images = get_images_in_dir_cached(directory)

    if not images:
        return None

    selected = images[_stable_image_index(seed, len(images))]
    try:
        rel_path = directory.relative_to(UPLOAD_DIR)
        selected_path = directory / selected
        return _public_static_url(f"/static/{rel_path.as_posix()}/{selected}", base_url, selected_path)
    except ValueError:
        return None


def _resolve_meeting_cover(meeting: Meeting, meeting_type: Optional[MeetingType], base_url: str) -> tuple[Optional[str], Optional[str]]:
    if meeting.cover_image:
        return _normalize_public_image_url(meeting.cover_image, base_url), "meeting"

    if meeting_type and meeting_type.is_fixed_image and meeting_type.cover_image:
        return _normalize_public_image_url(meeting_type.cover_image, base_url), "meeting_type"

    type_name = meeting_type.name if meeting_type else "default"
    bg_root = UPLOAD_DIR / "meeting_backgrounds"
    stable_seed = f"{meeting.id}:{meeting.meeting_type_id}:{meeting.start_time.isoformat()}:{type_name}"

    type_folder = bg_root / type_name
    type_url = get_stable_image_from_dir(type_folder, base_url, stable_seed)
    if type_url:
        return type_url, "type_directory"

    common_url = get_stable_image_from_dir(bg_root / "common", base_url, stable_seed)
    if common_url:
        return common_url, "common_directory"

    default_key = _classify_default_source(type_name)
    default_path = DEFAULT_IMAGES[default_key]
    return _normalize_public_image_url(default_path, base_url), "default"

@router.get("/", response_model=List[MeetingCardResponse])
def read_meetings(
    request: Request,
    skip: int = 0, 
    limit: int = 100, 
    status: Optional[str] = None,
    sort: Optional[str] = "desc", # asc, desc
    start_date: Optional[str] = None, # YYYY-MM-DD
    end_date: Optional[str] = None, # YYYY-MM-DD
    user_id: Optional[int] = None,
    force_show_all: bool = False, # Admin flag to ignore visibility timeout
    session: Session = Depends(get_session)
):
    """
    查询会议列表 (带封面图逻辑)
    """
    query = select(Meeting)
    if status:
        query = query.where(Meeting.status == status)
    
    # Date Filtering (Using CST - China Standard Time)
    # The Android client sends dates in CST, so we need to match
    from datetime import timezone
    cst_tz = timezone(timedelta(hours=8))
    
    if start_date:
        try:
            # Parse start of day in CST, then convert to naive datetime for DB comparison
            s_dt = datetime.strptime(start_date, "%Y-%m-%d")
            # If server is in UTC, the DB stores naive datetimes which are effectively CST
            # So we compare directly without conversion
            print(f"[DEBUG] Filtering start_date >= {s_dt}")
            query = query.where(Meeting.start_time >= s_dt)
        except ValueError as e:
            print(f"[DEBUG] start_date parse error: {e}")
            pass
            
    if end_date:
        try:
            # Parse end of day (23:59:59)
            e_dt = datetime.strptime(end_date, "%Y-%m-%d").replace(hour=23, minute=59, second=59)
            print(f"[DEBUG] Filtering end_date <= {e_dt}")
            query = query.where(Meeting.start_time <= e_dt)
        except ValueError as e:
            print(f"[DEBUG] end_date parse error: {e}")
            pass

    # Sorting
    if sort == "asc":
        query = query.order_by(Meeting.start_time.asc())
    else:
        query = query.order_by(Meeting.start_time.desc())

    # [Start] Visibility Timeout Logic - MUST BE BEFORE offset/limit
    # 默认过滤掉超时的会议，除非显式请求 force_show_all
    meetings = session.exec(query).all()
    attending_meeting_ids = _get_attending_meeting_ids_for_user(user_id, session) if user_id else None
    if attending_meeting_ids is not None:
        meetings = [meeting for meeting in meetings if meeting.id in attending_meeting_ids]
    checkin_map = _get_checkin_map_for_user(meetings, user_id, session)
    meetings = [
        meeting for meeting in meetings
        if _is_meeting_visible_for_android(
            meeting,
            session=session,
            force_show_all=force_show_all,
            checkin=checkin_map.get(meeting.id)
        )
    ][skip: skip + limit]
    
    results = []
    all_types = {t.id: t for t in session.exec(select(MeetingType)).all()}
    
    base_url = str(request.base_url) # http://.../ with trailing slash usually
    if not base_url.endswith("/"): base_url += "/"
    
    for m in meetings:
        checkin = checkin_map.get(m.id)
        resp = MeetingCardResponse(
            id=m.id,
            title=m.title,
            meeting_type_id=m.meeting_type_id,
            cover_image=m.cover_image,
            start_time=m.start_time,
            end_time=m.end_time,
            location=m.location,
            speaker=m.speaker,
            agenda=m.agenda,
            status=m.status,
            created_at=m.created_at,
            attachments=[AttachmentRead(**attachment.model_dump()) for attachment in (m.attachments or [])],
            agenda_items=[AgendaItemOutput(**item) for item in _normalize_agenda_items(agenda_raw=m.agenda)],
            meeting_contacts=_parse_meeting_contacts(m.meeting_contacts),
            show_media_link=m.show_media_link,
            android_visibility_mode=(m.android_visibility_mode or "inherit"),
            android_visibility_hide_after_hours=m.android_visibility_hide_after_hours,
            is_checked_in=checkin is not None,
            checkin_id=checkin.id if checkin else None,
            check_in_time=checkin.check_in_time if checkin else None,
            is_today_meeting=_meeting_occurs_today_in_shanghai(m)
        )
        m_type = all_types.get(m.meeting_type_id)
        final_url, image_source = _resolve_meeting_cover(m, m_type, base_url)
        resp.card_image_url = final_url
        resp.card_image_thumb_url = build_thumbnail_url(final_url, base_url)
        resp.card_image_source = image_source
        resp.meeting_type_name = m_type.name if m_type else "普通会议"
        results.append(resp)
        
    return results

class MeetingWithAttachments(MeetingCardResponse):
    attendees: List[AttendeeOutput] = []

@router.get("/{meeting_id}", response_model=MeetingWithAttachments)
def read_meeting(
    meeting_id: int, 
    request: Request,
    user_id: Optional[int] = None,
    force_show_all: bool = False,
    session: Session = Depends(get_session)
):
    """
    获取单个会议详情 (包含附件、封面、类型名)
    """
    meeting = session.get(Meeting, meeting_id)
    if not meeting:
        raise HTTPException(status_code=404, detail="Meeting not found")
    if user_id and meeting_id not in _get_attending_meeting_ids_for_user(user_id, session):
        raise HTTPException(status_code=404, detail="Meeting not found")
    checkin = _get_checkin_map_for_user([meeting], user_id, session).get(meeting_id)
    if not _is_meeting_visible_for_android(
        meeting,
        session=session,
        force_show_all=force_show_all,
        checkin=checkin
    ):
        raise HTTPException(status_code=404, detail="Meeting not found")

    resp = MeetingWithAttachments(
        id=meeting.id,
        title=meeting.title,
        meeting_type_id=meeting.meeting_type_id,
        cover_image=meeting.cover_image,
        start_time=meeting.start_time,
        end_time=meeting.end_time,
        location=meeting.location,
        speaker=meeting.speaker,
        agenda=meeting.agenda,
        status=meeting.status,
        created_at=meeting.created_at,
        attachments=[AttachmentRead(**attachment.model_dump()) for attachment in (meeting.attachments or [])],
        agenda_items=[AgendaItemOutput(**item) for item in _normalize_agenda_items(agenda_raw=meeting.agenda)],
        meeting_contacts=_parse_meeting_contacts(meeting.meeting_contacts),
        show_media_link=meeting.show_media_link,
        android_visibility_mode=(meeting.android_visibility_mode or "inherit"),
        android_visibility_hide_after_hours=meeting.android_visibility_hide_after_hours,
        is_checked_in=checkin is not None,
        checkin_id=checkin.id if checkin else None,
        check_in_time=checkin.check_in_time if checkin else None,
        is_today_meeting=_meeting_occurs_today_in_shanghai(meeting)
    )
    # 补充 computed fields
    m_type = session.get(MeetingType, meeting.meeting_type_id) if meeting.meeting_type_id else None
    resp.meeting_type_name = m_type.name if m_type else "普通会议"
    
    # 图片逻辑复用 (简化版)
    base_url = str(request.base_url)
    if not base_url.endswith("/"): base_url += "/"
    final_url, image_source = _resolve_meeting_cover(meeting, m_type, base_url)
    resp.card_image_url = final_url
    resp.card_image_thumb_url = build_thumbnail_url(final_url, base_url)
    resp.card_image_source = image_source
    
    # 填充与会者角色列表
    attendee_links = session.exec(select(MeetingAttendeeLink).where(MeetingAttendeeLink.meeting_id == meeting_id)).all()
    if attendee_links:
        user_ids = [link.user_id for link in attendee_links]
        users = session.exec(select(User).where(User.id.in_(user_ids))).all()
        user_map = {u.id: u.name for u in users}
        
        resp.attendees.extend([
            AttendeeOutput(
                type="user",
                user_id=link.user_id,
                name=user_map.get(link.user_id, "未知"),
                meeting_role=link.meeting_role
            )
            for link in attendee_links
        ])

    resp.attendees.extend(_parse_manual_attendees(meeting.manual_attendees))
        
    return resp

@router.put("/{meeting_id}", response_model=Meeting)
def update_meeting(
    meeting_id: int, 
    meeting_update: MeetingUpdateInput, 
    session: Session = Depends(get_session)
):
    """更新会议基本信息"""
    db_meeting = session.get(Meeting, meeting_id)
    if not db_meeting:
        raise HTTPException(status_code=404, detail="Meeting not found")
    previous_cover = db_meeting.cover_image
        
    meeting_data = meeting_update.model_dump(
        exclude_unset=True,
        exclude={"attendees_roles", "attendee_entries", "agenda_items", "meeting_contacts"}
    )
    if "cover_image" in meeting_data:
        meeting_data["cover_image"] = _normalize_optional_image_url(meeting_data.get("cover_image"))
    if "android_visibility_mode" in meeting_data or "android_visibility_hide_after_hours" in meeting_data:
        existing_mode = db_meeting.android_visibility_mode or "inherit"
        existing_hours = db_meeting.android_visibility_hide_after_hours
        meeting_data["android_visibility_mode"] = meeting_data.get("android_visibility_mode", existing_mode)
        meeting_data["android_visibility_hide_after_hours"] = meeting_data.get(
            "android_visibility_hide_after_hours",
            existing_hours
        )
        meeting_data = _apply_android_visibility_defaults(meeting_data)
    for key, value in meeting_data.items():
        if key in ('start_time', 'end_time') and isinstance(value, str):
             try:
                # 再次防止 Pydantic 转 datetime 失败的情况
                value = datetime.fromisoformat(value.replace('Z', '+00:00'))
             except: pass
        setattr(db_meeting, key, value)

    if "agenda_items" in meeting_update.model_fields_set or "agenda" in meeting_update.model_fields_set:
        db_meeting.agenda = _serialize_agenda_items(meeting_update.agenda_items, meeting_update.agenda)

    if "meeting_contacts" in meeting_update.model_fields_set:
        db_meeting.meeting_contacts = _serialize_meeting_contacts(meeting_update.meeting_contacts)

    session.add(db_meeting)
    
    if "attendee_entries" in meeting_update.model_fields_set or "attendees_roles" in meeting_update.model_fields_set:
        # 删除旧的关联
        session.exec(delete(MeetingAttendeeLink).where(MeetingAttendeeLink.meeting_id == meeting_id))
        user_entries, manual_entries = _split_attendees_payload(
            attendee_entries=meeting_update.attendee_entries,
            attendees_roles=meeting_update.attendees_roles
        )
        db_meeting.manual_attendees = _serialize_manual_attendees(manual_entries)
        # 插入新的用户关联
        for attendee in user_entries:
            link = MeetingAttendeeLink(
                meeting_id=meeting_id,
                user_id=attendee["user_id"],
                meeting_role=attendee["meeting_role"]
            )
            session.add(link)
            
    session.commit()
    session.refresh(db_meeting)
    if previous_cover != db_meeting.cover_image:
        _delete_meeting_cover_if_unused(session, previous_cover, exclude_meeting_id=meeting_id)
    _cleanup_stale_meeting_covers(session, keep_urls={db_meeting.cover_image} if db_meeting.cover_image else set())

    try:
        sio.start_background_task(
            broadcast_meeting_changed,
            "updated",
            {
                "meeting_id": db_meeting.id,
                "title": db_meeting.title,
                "start_time": db_meeting.start_time.isoformat() if db_meeting.start_time else None
            }
        )
    except Exception as e:
        print(f"[Socket.IO] failed to broadcast meeting_changed: {e}")

    return db_meeting

@router.delete("/{meeting_id}")
def delete_meeting(meeting_id: int, session: Session = Depends(get_session)):
    """删除会议 (级联删除附件)"""
    meeting = session.get(Meeting, meeting_id)
    if not meeting:
        raise HTTPException(status_code=404, detail="Meeting not found")
    previous_cover = meeting.cover_image
    
    # 删除关联的物理文件
    for attachment in meeting.attachments:
        try:
            if os.path.exists(attachment.file_path):
                os.remove(attachment.file_path)
        except Exception as e:
            print(f"Error deleting file {attachment.file_path}: {e}")
    # 手动级联删除关联投票
    votes = session.exec(select(Vote).where(Vote.meeting_id == meeting_id)).all()
    for vote in votes:
        session.exec(delete(UserVote).where(UserVote.vote_id == vote.id))
        session.exec(delete(VoteOption).where(VoteOption.vote_id == vote.id))
        session.delete(vote)

    session.delete(meeting)
    session.commit()
    _delete_meeting_cover_if_unused(session, previous_cover, exclude_meeting_id=meeting_id)
    _cleanup_stale_meeting_covers(session)
    return {"ok": True}

import os

@router.post("/{meeting_id}/upload")
def upload_file(
    meeting_id: int,
    file: UploadFile = File(...),
    session: Session = Depends(get_session)
):
    """
    上传会议附件
    """
    meeting = session.get(Meeting, meeting_id)
    if not meeting:
        raise HTTPException(status_code=404, detail="Meeting not found")

    # 安全校验：文件名路径穿越防护
    original_filename = file.filename or "unnamed"
    # 去除路径分隔符和 .. 防止路径穿越
    safe_original = os.path.basename(original_filename).replace("..", "")
    if not safe_original:
        raise HTTPException(status_code=400, detail="无效的文件名")

    # 安全校验：扩展名白名单
    _, ext = os.path.splitext(safe_original)
    if ext.lower() not in ALLOWED_EXTENSIONS:
        raise HTTPException(
            status_code=400,
            detail=f"不支持的文件格式 '{ext}'，仅允许: {', '.join(ALLOWED_EXTENSIONS)}"
        )

    # 安全校验：文件大小限制（先读取内容检查大小）
    contents = file.file.read()
    if len(contents) > MAX_UPLOAD_SIZE:
        raise HTTPException(
            status_code=400,
            detail=f"文件大小超过限制（最大 {MAX_UPLOAD_SIZE // 1024 // 1024}MB）"
        )

    # 生成安全的文件名
    timestamp = datetime.now().strftime("%Y%m%d%H%M%S")
    safe_filename = f"{timestamp}_{safe_original}"
    file_path = UPLOAD_DIR / safe_filename

    # 保存文件
    with open(file_path, "wb") as buffer:
        buffer.write(contents)

    # 获取文件大小
    file_size = len(contents)

    # 记录数据库
    attachment = Attachment(
        filename=safe_filename,
        display_name=safe_original,  # 使用清洗后的原始文件名
        file_path=str(file_path),
        file_size=file_size,
        content_type=file.content_type or "application/octet-stream",
        meeting_id=meeting.id
    )
    session.add(attachment)
    session.commit()
    session.refresh(attachment)

    try:
        sio.start_background_task(
            broadcast_meeting_changed,
            "attachment_uploaded",
            {
                "meeting_id": meeting.id,
                "attachment_id": attachment.id
            }
        )
    except Exception as e:
        print(f"[Socket.IO] failed to broadcast attachment_uploaded: {e}")

    return attachment

class AttachmentUpdate(SQLModel):
    display_name: Optional[str] = None
    sort_order: Optional[int] = None

@router.put("/attachments/{attachment_id}", response_model=AttachmentRead)
def update_attachment(
    attachment_id: int, 
    update_data: AttachmentUpdate, 
    session: Session = Depends(get_session)
):
    """更新附件信息 (重命名, 排序)"""
    attachment = session.get(Attachment, attachment_id)
    if not attachment:
        raise HTTPException(status_code=404, detail="Attachment not found")
    
    if update_data.display_name is not None:
        attachment.display_name = update_data.display_name
    if update_data.sort_order is not None:
        attachment.sort_order = update_data.sort_order
        
    session.add(attachment)
    session.commit()
    session.refresh(attachment)

    try:
        sio.start_background_task(
            broadcast_meeting_changed,
            "attachment_updated",
            {
                "meeting_id": attachment.meeting_id,
                "attachment_id": attachment.id
            }
        )
    except Exception as e:
        print(f"[Socket.IO] failed to broadcast attachment_updated: {e}")

    return attachment

@router.delete("/attachments/{attachment_id}")
def delete_attachment(attachment_id: int, session: Session = Depends(get_session)):
    """删除附件"""
    attachment = session.get(Attachment, attachment_id)
    if not attachment:
        raise HTTPException(status_code=404, detail="Attachment not found")
    meeting_id = attachment.meeting_id
        
    # 删除物理文件
    try:
        if os.path.exists(attachment.file_path):
            os.remove(attachment.file_path)
    except Exception as e:
        print(f"Error deleting file: {e}")

    session.delete(attachment)
    session.commit()

    try:
        sio.start_background_task(
            broadcast_meeting_changed,
            "attachment_deleted",
            {
                "meeting_id": meeting_id,
                "attachment_id": attachment_id
            }
        )
    except Exception as e:
        print(f"[Socket.IO] failed to broadcast attachment_deleted: {e}")

    return {"ok": True}
