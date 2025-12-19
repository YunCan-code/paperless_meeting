from fastapi import APIRouter, Depends, HTTPException, UploadFile, File, Form, Request
from sqlmodel import Session, select, SQLModel
from typing import List, Optional
import shutil
from pathlib import Path
from datetime import datetime

from database import get_session
from models import Meeting, MeetingType, Attachment, User, MeetingRead, AttachmentRead, MeetingBase

# 创建路由器，前缀为 /meetings
router = APIRouter(prefix="/meetings", tags=["meetings"])

# 定义上传文件存储目录
UPLOAD_DIR = Path("uploads")
# 确保目录存在
UPLOAD_DIR.mkdir(parents=True, exist_ok=True)

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

import random
import os

# Enhanced Response Model
class MeetingCardResponse(MeetingRead):
    card_image_url: Optional[str] = None
    meeting_type_name: Optional[str] = None

# Default Image Pool for Random Strategy (Fallback)
DEFAULT_IMAGES = {
    "weekly": "https://images.unsplash.com/photo-1431540015161-0bf868a2d407?q=80&w=2070&auto=format&fit=crop",
    "urgent": "https://images.unsplash.com/photo-1516387938699-a93567ec168e?q=80&w=2071&auto=format&fit=crop",
    "review": "https://images.unsplash.com/photo-1552664730-d307ca884978?q=80&w=2070&auto=format&fit=crop",
    "kickoff": "https://images.unsplash.com/photo-1522071820081-009f0129c71c?q=80&w=2070&auto=format&fit=crop",
    "default": "https://images.unsplash.com/photo-1497366216548-37526070297c?q=80&w=2069&auto=format&fit=crop"
}

def get_random_image_from_dir(directory: Path, base_url: str) -> Optional[str]:
    """Helper to pick random image from directory and return full URL"""
    if not directory.exists() or not directory.is_dir():
        return None
        
    valid_extensions = {'.jpg', '.jpeg', '.png', '.webp'}
    images = [
        f for f in os.listdir(directory) 
        if os.path.isfile(directory / f) and os.path.splitext(f)[1].lower() in valid_extensions
    ]
    
    if not images:
        return None
        
    selected = random.choice(images)
    # Convert 'uploads/meeting_backgrounds/...' to '/static/meeting_backgrounds/...'
    # Assumes directory starts with 'uploads/'
    # Path('uploads/meeting_backgrounds/common') -> parts: ('uploads', 'meeting_backgrounds', 'common')
    
    # Robust relative path calculation
    # We want route relative to 'uploads' folder, which is mounted at '/static'
    try:
        rel_path = directory.relative_to(UPLOAD_DIR) # e.g. meeting_backgrounds/common
        return f"{base_url}static/{rel_path.as_posix()}/{selected}"
    except ValueError:
        return None

@router.get("/", response_model=List[MeetingCardResponse])
def read_meetings(
    request: Request,
    skip: int = 0, 
    limit: int = 100, 
    status: Optional[str] = None,
    session: Session = Depends(get_session)
):
    """
    查询会议列表 (带封面图逻辑)
    """
    query = select(Meeting)
    if status:
        query = query.where(Meeting.status == status)
    
    query = query.offset(skip).limit(limit).order_by(Meeting.start_time.desc())
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

class MeetingWithAttachments(MeetingRead):
    attachments: List[AttachmentRead] = []

@router.get("/{meeting_id}", response_model=MeetingWithAttachments)
def read_meeting(meeting_id: int, session: Session = Depends(get_session)):
    """
    获取单个会议详情 (包含附件)
    """
    meeting = session.get(Meeting, meeting_id)
    if not meeting:
        raise HTTPException(status_code=404, detail="Meeting not found")
    return meeting

class MeetingUpdate(SQLModel):
    title: Optional[str] = None
    meeting_type_id: Optional[int] = None
    start_time: Optional[datetime] = None
    location: Optional[str] = None
    status: Optional[str] = None

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

    # 生成安全的文件名
    timestamp = datetime.now().strftime("%Y%m%d%H%M%S")
    safe_filename = f"{timestamp}_{file.filename}"
    file_path = UPLOAD_DIR / safe_filename

    # 保存文件
    with open(file_path, "wb") as buffer:
        shutil.copyfileobj(file.file, buffer)
    
    # 获取文件大小
    file_size = os.path.getsize(file_path)

    # 记录数据库
    attachment = Attachment(
        filename=safe_filename,
        display_name=file.filename, # 默认显示原始文件名
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
