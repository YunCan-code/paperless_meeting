from fastapi import APIRouter, Depends, HTTPException, Query, UploadFile, File, Form
from sqlmodel import Session, select
from typing import List, Optional
import os
import shutil
from datetime import datetime

from database import get_session
from models import AppUpdate, AppUpdateRead, AppUpdateBase

router = APIRouter(prefix="/updates", tags=["updates"])

# 确保上传目录存在
UPLOAD_DIR = "uploads/apk"
os.makedirs(UPLOAD_DIR, exist_ok=True)

@router.get("/latest", response_model=Optional[AppUpdateRead])
async def get_latest_version(session: Session = Depends(get_session)):
    """
    获取最新发布的版本信息
    """
    statement = select(AppUpdate).order_by(AppUpdate.version_code.desc())
    latest = session.exec(statement).first()
    return latest

@router.get("/", response_model=List[AppUpdateRead])
async def list_versions(
    skip: int = 0, 
    limit: int = 20, 
    session: Session = Depends(get_session)
):
    """
    获取版本历史列表
    """
    statement = select(AppUpdate).order_by(AppUpdate.created_at.desc()).offset(skip).limit(limit)
    versions = session.exec(statement).all()
    return versions

@router.post("/", response_model=AppUpdateRead)
async def release_version(
    version_code: int = Form(...),
    version_name: str = Form(...),
    release_notes: str = Form(""),
    is_force_update: bool = Form(False),
    file: UploadFile = File(None),
    download_url: Optional[str] = Form(None),
    session: Session = Depends(get_session)
):
    """
    发布新版本。
    必须提供 file (上传APK) 或者 download_url (外部链接) 其中之一。
    """
    final_url = download_url
    
    if file:
        file_path = os.path.join(UPLOAD_DIR, file.filename)
        with open(file_path, "wb") as buffer:
            shutil.copyfileobj(file.file, buffer)
        # 假设Nginx配置了 /static/apk 指向 uploads/apk
        # 或者直接通过后端提供静态文件服务
        # 这里为了简单，假设后端会通过 /static/apk/{filename} 提供访问，或者 upload 目录本身被挂载
        # 暂时使用后端相对路径，前端或APP需要拼接 base_url
        final_url = f"/static/apk/{file.filename}"
    
    if not final_url:
        raise HTTPException(status_code=400, detail="Must provide either file upload or download_url")

    new_update = AppUpdate(
        version_code=version_code,
        version_name=version_name,
        release_notes=release_notes,
        is_force_update=is_force_update,
        download_url=final_url,
        created_at=datetime.now()
    )
    
    session.add(new_update)
    session.commit()
    session.refresh(new_update)
    return new_update

@router.delete("/{id}")
async def delete_version(id: int, session: Session = Depends(get_session)):
    version = session.get(AppUpdate, id)
    if not version:
        raise HTTPException(status_code=404, detail="Version not found")
    
    # Optional: Delete file if local
    # ...
    
    session.delete(version)
    session.commit()
    return {"ok": True}
