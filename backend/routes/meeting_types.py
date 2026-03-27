from fastapi import APIRouter, Depends, HTTPException, File, UploadFile
from sqlmodel import Session, select
from typing import List
import os
from pathlib import Path
from datetime import datetime
import shutil
from database import get_session
from models import MeetingType, MeetingTypeRead

# 创建路由器，前缀为 /meeting_types
router = APIRouter(prefix="/meeting_types", tags=["meeting_types"])

UPLOAD_DIR = Path("uploads/meeting_type_covers")
UPLOAD_DIR.mkdir(parents=True, exist_ok=True)
LEGACY_UPLOAD_DIR = Path("uploads/covers")
LEGACY_UPLOAD_DIR.mkdir(parents=True, exist_ok=True)

ALLOWED_IMAGE_EXTENSIONS = {".jpg", ".jpeg", ".png", ".webp"}
MAX_IMAGE_SIZE = 5 * 1024 * 1024
STALE_FILE_TTL_SECONDS = 24 * 60 * 60


def _validate_image_upload(file: UploadFile) -> tuple[bytes, str, str]:
    original_name = file.filename or "cover"
    safe_name = os.path.basename(original_name).replace("..", "")
    stem = Path(safe_name).stem or "cover"
    extension = Path(safe_name).suffix.lower()

    if extension not in ALLOWED_IMAGE_EXTENSIONS:
        raise HTTPException(status_code=400, detail="仅支持 JPG、PNG、WebP 格式图片")

    content = file.file.read()
    if not content:
        raise HTTPException(status_code=400, detail="上传文件不能为空")
    if len(content) > MAX_IMAGE_SIZE:
        raise HTTPException(status_code=400, detail="图片大小不能超过 5MB")

    return content, extension, stem


def _resolve_cover_path(image_url: str | None) -> Path | None:
    if not image_url:
        return None
    if image_url.startswith("/static/meeting_type_covers/"):
        return UPLOAD_DIR / os.path.basename(image_url.replace("/static/meeting_type_covers/", "", 1))
    if image_url.startswith("/static/covers/"):
        return LEGACY_UPLOAD_DIR / os.path.basename(image_url.replace("/static/covers/", "", 1))
    return None


def _normalize_optional_image_url(image_url: str | None) -> str | None:
    normalized = (image_url or "").strip()
    return normalized or None


def _delete_cover_file_if_unused(session: Session, image_url: str | None, exclude_type_id: int | None = None) -> None:
    if not image_url:
        return
    statement = select(MeetingType).where(MeetingType.cover_image == image_url)
    if exclude_type_id is not None:
        statement = statement.where(MeetingType.id != exclude_type_id)
    if session.exec(statement).first() is not None:
        return

    file_path = _resolve_cover_path(image_url)
    try:
        if file_path and file_path.exists() and file_path.is_file():
            file_path.unlink()
    except OSError:
        pass


def _cleanup_stale_type_covers(session: Session, keep_urls: set[str] | None = None) -> None:
    keep = keep_urls or set()
    referenced = {
        cover for cover in session.exec(select(MeetingType.cover_image)).all()
        if cover
    }
    cutoff = datetime.now().timestamp() - STALE_FILE_TTL_SECONDS

    for directory, prefix in (
        (UPLOAD_DIR, "/static/meeting_type_covers/"),
        (LEGACY_UPLOAD_DIR, "/static/covers/"),
    ):
        for file in directory.glob("*"):
            if not file.is_file():
                continue
            url = f"{prefix}{file.name}"
            if url in keep or url in referenced:
                continue
            try:
                if file.stat().st_mtime >= cutoff:
                    continue
                file.unlink()
            except OSError:
                pass

@router.post("/upload_cover")
def upload_cover(
    file: UploadFile = File(...),
    session: Session = Depends(get_session)
):
    """上传会议类型固定封面，返回静态 URL"""
    content, extension, stem = _validate_image_upload(file)
    version = str(int(datetime.now().timestamp()))
    safe_stem = "".join(ch if ch.isalnum() or ch in {"-", "_"} else "_" for ch in stem).strip("_") or "cover"
    safe_filename = f"{safe_stem}_{version}{extension}"
    file_path = UPLOAD_DIR / safe_filename

    with open(file_path, "wb") as buffer:
        buffer.write(content)

    cover_url = f"/static/meeting_type_covers/{safe_filename}"
    _cleanup_stale_type_covers(session, keep_urls={cover_url})
    return {"url": cover_url, "version": version}

@router.post("/", response_model=MeetingTypeRead)
def create_meeting_type(meeting_type: MeetingType, session: Session = Depends(get_session)):
    """
    创建新会议类型
    """
    if not meeting_type.is_fixed_image:
        meeting_type.cover_image = None
    else:
        meeting_type.cover_image = _normalize_optional_image_url(meeting_type.cover_image)
    session.add(meeting_type)
    session.commit()
    session.refresh(meeting_type)
    _cleanup_stale_type_covers(session, keep_urls={meeting_type.cover_image} if meeting_type.cover_image else set())
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
    
    previous_cover = meeting_type.cover_image

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
    _delete_cover_file_if_unused(session, previous_cover, exclude_type_id=type_id)
    _cleanup_stale_type_covers(session)
    return {"ok": True}

@router.put("/{type_id}", response_model=MeetingTypeRead)
def update_meeting_type(type_id: int, data: MeetingType, session: Session = Depends(get_session)):
    """
    更新会议类型
    """
    meeting_type = session.get(MeetingType, type_id)
    if not meeting_type:
        raise HTTPException(status_code=404, detail="Meeting Type not found")
    previous_cover = meeting_type.cover_image
    meeting_type.name = data.name
    meeting_type.description = data.description
    meeting_type.is_fixed_image = data.is_fixed_image
    meeting_type.cover_image = _normalize_optional_image_url(data.cover_image) if data.is_fixed_image else None
    session.add(meeting_type)
    session.commit()
    session.refresh(meeting_type)
    if previous_cover != meeting_type.cover_image:
        _delete_cover_file_if_unused(session, previous_cover, exclude_type_id=type_id)
    _cleanup_stale_type_covers(session, keep_urls={meeting_type.cover_image} if meeting_type.cover_image else set())
    return meeting_type
