from fastapi import APIRouter, Depends, HTTPException, UploadFile, File, Form, Request
from sqlmodel import Session, select, SQLModel, delete
from sqlalchemy import func
from typing import List, Optional
import shutil
from pathlib import Path
from datetime import datetime, timedelta

from database import get_session
from models import Meeting, MeetingType, Attachment, User, MeetingRead, AttachmentRead, MeetingBase, Vote, VoteOption, UserVote

# 创建路由器，前缀为 /meetings
router = APIRouter(prefix="/meetings", tags=["meetings"])

# 定义上传文件存储目录
UPLOAD_DIR = Path("uploads")
# 确保目录存在
UPLOAD_DIR.mkdir(parents=True, exist_ok=True)

# 文件上传安全限制
ALLOWED_EXTENSIONS = {'.pdf'}  # 仅允许 PDF 文件（与前端一致）
MAX_UPLOAD_SIZE = 200 * 1024 * 1024  # 200MB

@router.post("/", response_model=Meeting)
def create_meeting(meeting: Meeting, session: Session = Depends(get_session)):
    """
    创建新会议
    """
    # 修复: Pydantic 有时未能将 ISO 字符串自动转换为 datetime 对象，导致 SQLite 报错
    if isinstance(meeting.start_time, str):
        try:
            # 处理 ISO 格式 (例如 2025-12-16T16:00:00.000Z)
            meeting.start_time = datetime.fromisoformat(meeting.start_time.replace('Z', '+00:00'))
        except ValueError:
            pass # 如果转换失败，留给数据库报错或保留原值

    session.add(meeting)
    session.commit()
    session.refresh(meeting)
    return meeting


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

import random
import os

# Enhanced Response Model
class MeetingCardResponse(MeetingRead):
    card_image_url: Optional[str] = None
    meeting_type_name: Optional[str] = None
    attachments: List[AttachmentRead] = []

# Default Image Pool for Random Strategy (Fallback)
DEFAULT_IMAGES = {
    "weekly": "https://images.unsplash.com/photo-1431540015161-0bf868a2d407?q=80&w=2070&auto=format&fit=crop",
    "urgent": "https://images.unsplash.com/photo-1516387938699-a93567ec168e?q=80&w=2071&auto=format&fit=crop",
    "review": "https://images.unsplash.com/photo-1552664730-d307ca884978?q=80&w=2070&auto=format&fit=crop",
    "kickoff": "https://images.unsplash.com/photo-1522071820081-009f0129c71c?q=80&w=2070&auto=format&fit=crop",
    "default": "https://images.unsplash.com/photo-1497366216548-37526070297c?q=80&w=2069&auto=format&fit=crop"
}

from cachetools import TTLCache
import os

# 带 TTL 的缓存：最多缓存 32 个目录，60 秒后自动失效
_image_dir_cache = TTLCache(maxsize=32, ttl=60)

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
        except OSError:
            result = []

    _image_dir_cache[cache_key] = result
    return result

def get_random_image_from_dir(directory: Path, base_url: str) -> Optional[str]:
    """Helper to pick random image with caching"""
    images = get_images_in_dir_cached(directory)
    
    if not images:
        return None
        
    selected = random.choice(images)
    try:
        rel_path = directory.relative_to(UPLOAD_DIR)
        return f"{base_url}static/{rel_path.as_posix()}/{selected}"
    except ValueError:
        return None

@router.get("/", response_model=List[MeetingCardResponse])
def read_meetings(
    request: Request,
    skip: int = 0, 
    limit: int = 100, 
    status: Optional[str] = None,
    sort: Optional[str] = "desc", # asc, desc
    start_date: Optional[str] = None, # YYYY-MM-DD
    end_date: Optional[str] = None, # YYYY-MM-DD
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
    if not force_show_all:
        from models import SystemSetting
        hide_after_hours_setting = session.get(SystemSetting, "meeting_visibility_hide_after_hours")
        if hide_after_hours_setting and hide_after_hours_setting.value:
            try:
                hours = int(hide_after_hours_setting.value)
                if hours > 0:
                     # Calculate threshold time
                     now = datetime.now()
                     threshold = now - timedelta(hours=hours)
                     # Show only meetings where start_time > threshold
                     query = query.where(Meeting.start_time > threshold)
            except ValueError:
                pass
    # [End] Visibility Timeout Logic

    query = query.offset(skip).limit(limit)
    meetings = session.exec(query).all()
    
    results = []
    all_types = {t.id: t for t in session.exec(select(MeetingType)).all()}
    
    base_url = str(request.base_url) # http://.../ with trailing slash usually
    if not base_url.endswith("/"): base_url += "/"
    
    bg_root = UPLOAD_DIR / "meeting_backgrounds"
    
    for m in meetings:
        resp = MeetingCardResponse.model_validate(m)
        m_type = all_types.get(m.meeting_type_id)
        
        final_url = None
        
        # 1. Fixed Configuration
        if m_type and m_type.is_fixed_image and m_type.cover_image:
             raw_url = m_type.cover_image
             if raw_url and raw_url.startswith("/"):
                final_url = f"{base_url.rstrip('/')}{raw_url}"
             else:
                final_url = raw_url
        
        # 2. Random Strategy (Folder based > Common > Fallback)
        else:
            type_name = m_type.name if m_type else "default"
            
            # A. Try Type Specific Folder
            # Sanitize minimal bad chars? OS usually handles unicode.
            type_folder = bg_root / type_name
            final_url = get_random_image_from_dir(type_folder, base_url)
            
            # B. Try Common Folder
            if not final_url:
                common_folder = bg_root / "common"
                final_url = get_random_image_from_dir(common_folder, base_url)
            
            # C. Fallback to Hardcoded Unsplash
            if not final_url:
                if "周" in type_name or "例" in type_name:
                    final_url = DEFAULT_IMAGES["weekly"]
                elif "急" in type_name:
                    final_url = DEFAULT_IMAGES["urgent"]
                elif "评" in type_name or "审" in type_name:
                    final_url = DEFAULT_IMAGES["review"]
                elif "启" in type_name:
                    final_url = DEFAULT_IMAGES["kickoff"]
                else:
                    final_url = DEFAULT_IMAGES["default"]
        

        
        resp.card_image_url = final_url
        resp.meeting_type_name = m_type.name if m_type else "普通会议"
        results.append(resp)
        
    return results

class MeetingWithAttachments(MeetingCardResponse):
    attachments: List[AttachmentRead] = []

@router.get("/{meeting_id}", response_model=MeetingWithAttachments)
def read_meeting(
    meeting_id: int, 
    request: Request,
    session: Session = Depends(get_session)
):
    """
    获取单个会议详情 (包含附件、封面、类型名)
    """
    meeting = session.get(Meeting, meeting_id)
    if not meeting:
        raise HTTPException(status_code=404, detail="Meeting not found")

    # 构造响应对象
    resp = MeetingWithAttachments.model_validate(meeting)
    
    # 补充 computed fields
    m_type = session.get(MeetingType, meeting.meeting_type_id) if meeting.meeting_type_id else None
    resp.meeting_type_name = m_type.name if m_type else "普通会议"
    
    # 图片逻辑复用 (简化版)
    # TODO: Refactor into helper function
    base_url = str(request.base_url)
    if not base_url.endswith("/"): base_url += "/"
    bg_root = UPLOAD_DIR / "meeting_backgrounds"
    final_url = None

    if m_type and m_type.is_fixed_image and m_type.cover_image:
         raw_url = m_type.cover_image
         if raw_url and raw_url.startswith("/"):
            final_url = f"{base_url.rstrip('/')}{raw_url}"
         else:
            final_url = raw_url
    else:
        type_name = m_type.name if m_type else "default"
        type_folder = bg_root / type_name
        final_url = get_random_image_from_dir(type_folder, base_url)
        
        if not final_url:
            common_folder = bg_root / "common"
            final_url = get_random_image_from_dir(common_folder, base_url)
        
        if not final_url:
            # Fallback map
            if "周" in type_name or "例" in type_name:
                final_url = DEFAULT_IMAGES["weekly"]
            elif "急" in type_name:
                final_url = DEFAULT_IMAGES["urgent"]
            elif "评" in type_name or "审" in type_name:
                final_url = DEFAULT_IMAGES["review"]
            elif "启" in type_name:
                final_url = DEFAULT_IMAGES["kickoff"]
            else:
                final_url = DEFAULT_IMAGES["default"]

    resp.card_image_url = final_url
    return resp

class MeetingUpdate(SQLModel):
    title: Optional[str] = None
    meeting_type_id: Optional[int] = None
    start_time: Optional[datetime] = None
    location: Optional[str] = None
    status: Optional[str] = None
    speaker: Optional[str] = None
    agenda: Optional[str] = None

@router.put("/{meeting_id}", response_model=Meeting)
def update_meeting(
    meeting_id: int, 
    meeting_update: MeetingUpdate, 
    session: Session = Depends(get_session)
):
    """更新会议基本信息"""
    db_meeting = session.get(Meeting, meeting_id)
    if not db_meeting:
        raise HTTPException(status_code=404, detail="Meeting not found")
        
    meeting_data = meeting_update.model_dump(exclude_unset=True)
    for key, value in meeting_data.items():
        if key == 'start_time' and isinstance(value, str):
             try:
                # 再次防止 Pydantic 转 datetime 失败的情况
                value = datetime.fromisoformat(value.replace('Z', '+00:00'))
             except: pass
        setattr(db_meeting, key, value)
        
    session.add(db_meeting)
    session.commit()
    session.refresh(db_meeting)
    return db_meeting

@router.delete("/{meeting_id}")
def delete_meeting(meeting_id: int, session: Session = Depends(get_session)):
    """删除会议 (级联删除附件)"""
    meeting = session.get(Meeting, meeting_id)
    if not meeting:
        raise HTTPException(status_code=404, detail="Meeting not found")
    
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
    return attachment

@router.delete("/attachments/{attachment_id}")
def delete_attachment(attachment_id: int, session: Session = Depends(get_session)):
    """删除附件"""
    attachment = session.get(Attachment, attachment_id)
    if not attachment:
        raise HTTPException(status_code=404, detail="Attachment not found")
        
    # 删除物理文件
    try:
        if os.path.exists(attachment.file_path):
            os.remove(attachment.file_path)
    except Exception as e:
        print(f"Error deleting file: {e}")

    session.delete(attachment)
    session.commit()
    return {"ok": True}
