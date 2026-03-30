from datetime import datetime
from pathlib import Path
from typing import Any
import os
import uuid

from fastapi import APIRouter, Depends, File, HTTPException, UploadFile
from sqlmodel import Session, select

from database import get_session
from models import MeetingType, SystemSetting

router = APIRouter(prefix="/cover_center", tags=["cover_center"])

MEETING_BACKGROUND_ROOT = Path("uploads/meeting_backgrounds")
COMMON_POOL_DIR = MEETING_BACKGROUND_ROOT / "common"
DEFAULT_MEETING_DIR = Path("uploads/meeting_defaults")

ALLOWED_IMAGE_EXTENSIONS = {".jpg", ".jpeg", ".png", ".webp"}
MAX_IMAGE_SIZE = 5 * 1024 * 1024

MEETING_BACKGROUND_ROOT.mkdir(parents=True, exist_ok=True)
COMMON_POOL_DIR.mkdir(parents=True, exist_ok=True)
DEFAULT_MEETING_DIR.mkdir(parents=True, exist_ok=True)

IMPORTANT_SETTING_KEYS = {
    "default_meeting_location",
    "meeting_visibility_hide_after_hours",
    "android_login_poster_url",
    "android_login_poster_version",
}


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


def _safe_stem(stem: str) -> str:
    return "".join(ch if ch.isalnum() or ch in {"-", "_"} else "_" for ch in stem).strip("_") or "cover"


def _build_pool_item(file_path: Path, url_prefix: str) -> dict[str, Any]:
    stat = file_path.stat()
    return {
        "name": file_path.name,
        "url": f"{url_prefix}/{file_path.name}",
        "size": stat.st_size,
        "updated_at": datetime.fromtimestamp(stat.st_mtime).isoformat(),
    }


def _list_pool_files(directory: Path, url_prefix: str) -> list[dict[str, Any]]:
    if not directory.exists():
        return []

    files = [
        _build_pool_item(file_path, url_prefix)
        for file_path in directory.iterdir()
        if file_path.is_file() and file_path.suffix.lower() in ALLOWED_IMAGE_EXTENSIONS
    ]
    files.sort(key=lambda item: item["updated_at"], reverse=True)
    return files


def _get_type_or_404(session: Session, type_id: int) -> MeetingType:
    meeting_type = session.get(MeetingType, type_id)
    if not meeting_type:
        raise HTTPException(status_code=404, detail="会议类型不存在")
    return meeting_type


def _get_type_pool_dir(meeting_type: MeetingType) -> Path:
    directory = MEETING_BACKGROUND_ROOT / meeting_type.name
    directory.mkdir(parents=True, exist_ok=True)
    return directory


def _delete_pool_file(directory: Path, filename: str) -> None:
    safe_name = os.path.basename(filename)
    if not safe_name:
        raise HTTPException(status_code=400, detail="文件名无效")

    file_path = directory / safe_name
    if not file_path.exists() or not file_path.is_file():
        raise HTTPException(status_code=404, detail="封面文件不存在")

    try:
        file_path.unlink()
    except OSError as exc:
        raise HTTPException(status_code=500, detail=f"删除文件失败: {exc}") from exc


@router.get("/overview")
def get_cover_center_overview(session: Session = Depends(get_session)):
    settings = {
        setting.key: setting.value
        for setting in session.exec(select(SystemSetting)).all()
        if setting.key in IMPORTANT_SETTING_KEYS
    }

    meeting_types = session.exec(select(MeetingType).order_by(MeetingType.created_at.desc())).all()
    type_items = []
    for meeting_type in meeting_types:
        type_pool = _list_pool_files(
            _get_type_pool_dir(meeting_type),
            f"/static/meeting_backgrounds/{meeting_type.name}",
        )
        type_items.append(
            {
                "id": meeting_type.id,
                "name": meeting_type.name,
                "description": meeting_type.description,
                "created_at": meeting_type.created_at.isoformat(),
                "is_fixed_image": meeting_type.is_fixed_image,
                "cover_image": meeting_type.cover_image,
                "random_pool_count": len(type_pool),
                "random_pool": type_pool,
            }
        )

    return {
        "settings": settings,
        "default_images": _list_pool_files(DEFAULT_MEETING_DIR, "/static/meeting_defaults"),
        "common_pool": _list_pool_files(COMMON_POOL_DIR, "/static/meeting_backgrounds/common"),
        "meeting_types": type_items,
    }


@router.post("/common/upload")
def upload_common_cover(
    file: UploadFile = File(...),
):
    content, extension, stem = _validate_image_upload(file)
    safe_filename = f"{_safe_stem(stem)}_{int(datetime.now().timestamp())}_{uuid.uuid4().hex[:8]}{extension}"
    file_path = COMMON_POOL_DIR / safe_filename

    with open(file_path, "wb") as output:
        output.write(content)

    return _build_pool_item(file_path, "/static/meeting_backgrounds/common")


@router.delete("/common/{filename}")
def delete_common_cover(filename: str):
    _delete_pool_file(COMMON_POOL_DIR, filename)
    return {"ok": True}


@router.post("/type/{type_id}/upload")
def upload_type_random_cover(
    type_id: int,
    file: UploadFile = File(...),
    session: Session = Depends(get_session),
):
    meeting_type = _get_type_or_404(session, type_id)
    directory = _get_type_pool_dir(meeting_type)
    content, extension, stem = _validate_image_upload(file)
    safe_filename = f"{_safe_stem(stem)}_{int(datetime.now().timestamp())}_{uuid.uuid4().hex[:8]}{extension}"
    file_path = directory / safe_filename

    with open(file_path, "wb") as output:
        output.write(content)

    return _build_pool_item(file_path, f"/static/meeting_backgrounds/{meeting_type.name}")


@router.delete("/type/{type_id}/{filename}")
def delete_type_random_cover(
    type_id: int,
    filename: str,
    session: Session = Depends(get_session),
):
    meeting_type = _get_type_or_404(session, type_id)
    directory = _get_type_pool_dir(meeting_type)
    _delete_pool_file(directory, filename)
    return {"ok": True}
