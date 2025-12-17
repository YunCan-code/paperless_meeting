from fastapi import APIRouter, Depends, HTTPException, UploadFile, File, Form
from sqlmodel import Session, select, SQLModel
from typing import List, Optional
import shutil
from pathlib import Path
from datetime import datetime

from database import get_session
from models import Meeting, MeetingType, Attachment, User

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

@router.get("/", response_model=List[Meeting])
def read_meetings(
    skip: int = 0, 
    limit: int = 100, 
    status: Optional[str] = None,
    session: Session = Depends(get_session)
):
    """
    查询会议列表
    - skip: 跳过前几条 (分页用)
    - limit: 返回多少条 (分页用)
    - status: 按状态过滤 (scheduled, active, finished)
    """
    query = select(Meeting)
    if status:
        query = query.where(Meeting.status == status)
    # 按开始时间倒序排列 (最新的在前)
    query = query.offset(skip).limit(limit).order_by(Meeting.start_time.desc())
    meetings = session.exec(query).all()
    return meetings

@router.get("/{meeting_id}", response_model=Meeting)
def read_meeting(meeting_id: int, session: Session = Depends(get_session)):
    """
    获取单个会议详情
    """
    meeting = session.get(Meeting, meeting_id)
    if not meeting:
        raise HTTPException(status_code=404, detail="Meeting not found")
    return meeting

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

@router.put("/attachments/{attachment_id}", response_model=Attachment)
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
