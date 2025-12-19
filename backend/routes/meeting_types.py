from fastapi import APIRouter, Depends, HTTPException, File, UploadFile, Request
from sqlmodel import Session, select
from typing import List
import shutil
import os
from pathlib import Path
from datetime import datetime
from database import get_session
from models import MeetingType, MeetingTypeRead

# 创建路由器，前缀为 /meeting_types
router = APIRouter(prefix="/meeting_types", tags=["meeting_types"])

UPLOAD_DIR = Path("uploads/covers")
UPLOAD_DIR.mkdir(parents=True, exist_ok=True)

@router.post("/upload_cover")
def upload_cover(
    file: UploadFile = File(...), 
):
    """上传封面图，返回相对路径"""
    timestamp = datetime.now().strftime("%Y%m%d%H%M%S")
    safe_filename = f"{timestamp}_{file.filename}"
    file_path = UPLOAD_DIR / safe_filename
    
    with open(file_path, "wb") as buffer:
        shutil.copyfileobj(file.file, buffer)
        
    # Return relative path
    return {"url": f"/static/covers/{safe_filename}"}

@router.post("/", response_model=MeetingTypeRead)
def create_meeting_type(meeting_type: MeetingType, session: Session = Depends(get_session)):
    """
    创建新会议类型
    """
    session.add(meeting_type)
    session.commit()
    session.refresh(meeting_type)
    return meeting_type

@router.get("/", response_model=List[MeetingTypeRead])
def read_meeting_types(session: Session = Depends(get_session)):
    """
    获取所有会议类型
    """
    types = session.exec(select(MeetingType)).all()
    return types

@router.delete("/{type_id}")
def delete_meeting_type(type_id: int, session: Session = Depends(get_session)):
    """
    删除会议类型 (同时删除关联的图片资源)
    """
    meeting_type = session.get(MeetingType, type_id)
    if not meeting_type:
        raise HTTPException(status_code=404, detail="Meeting Type not found")
    
    # 1. 删除固定封面图片
    # cover_image 格式为 /static/covers/filename
    if meeting_type.cover_image and meeting_type.cover_image.startswith("/static/covers/"):
        filename = meeting_type.cover_image.replace("/static/covers/", "")
        file_path = UPLOAD_DIR / filename
        try:
            if file_path.exists() and file_path.is_file():
                os.remove(file_path)
                print(f"Deleted fixed cover: {file_path}")
        except Exception as e:
            print(f"Error deleting fixed cover: {e}")

    # 2. 删除对应的随机图库文件夹
    # 路径: uploads/meeting_backgrounds/{name}
    bg_root = Path("uploads/meeting_backgrounds")
    type_folder = bg_root / meeting_type.name
    
    # 安全检查：禁止删除 'common' 文件夹，且文件夹必须存在
    if meeting_type.name.lower() != "common" and type_folder.exists() and type_folder.is_dir():
        try:
            shutil.rmtree(type_folder)
            print(f"Deleted image folder: {type_folder}")
        except Exception as e:
            print(f"Error deleting folder: {e}")

    session.delete(meeting_type)
    session.commit()
    return {"ok": True}

@router.put("/{type_id}", response_model=MeetingTypeRead)
def update_meeting_type(type_id: int, data: MeetingType, session: Session = Depends(get_session)):
    """
    更新会议类型
    """
    meeting_type = session.get(MeetingType, type_id)
    if not meeting_type:
        raise HTTPException(status_code=404, detail="Meeting Type not found")
    meeting_type.name = data.name
    meeting_type.description = data.description
    meeting_type.is_fixed_image = data.is_fixed_image
    meeting_type.cover_image = data.cover_image
    session.add(meeting_type)
    session.commit()
    session.refresh(meeting_type)
    return meeting_type
