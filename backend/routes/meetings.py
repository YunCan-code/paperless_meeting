from fastapi import APIRouter, Depends, HTTPException, UploadFile, File, Form
from sqlmodel import Session, select
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

    # 生成安全的文件名 (添加时间戳前缀，防止重名)
    timestamp = datetime.now().strftime("%Y%m%d%H%M%S")
    safe_filename = f"{timestamp}_{file.filename}"
    file_path = UPLOAD_DIR / safe_filename

    # 将上传的文件内容写入本地磁盘
    with open(file_path, "wb") as buffer:
        shutil.copyfileobj(file.file, buffer)

    # 在数据库中记录附件信息
    attachment = Attachment(
        filename=file.filename,
        file_path=str(file_path),
        meeting_id=meeting.id
    )
    session.add(attachment)
    session.commit()
    session.refresh(attachment)
    return attachment
