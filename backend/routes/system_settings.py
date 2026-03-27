from datetime import datetime
from pathlib import Path
from typing import Dict
import os

from fastapi import APIRouter, Depends, File, HTTPException, UploadFile
from sqlmodel import Session, select

from database import get_session
from models import SystemSetting
from socket_manager import sio, broadcast_meeting_changed

router = APIRouter(prefix="/settings", tags=["settings"])

ANDROID_VISIBILITY_SETTING_KEY = "meeting_visibility_hide_after_hours"
ANDROID_LOGIN_POSTER_URL_KEY = "android_login_poster_url"
ANDROID_LOGIN_POSTER_VERSION_KEY = "android_login_poster_version"

SETTINGS_UPLOAD_DIR = Path("uploads/settings")
SETTINGS_UPLOAD_DIR.mkdir(parents=True, exist_ok=True)

ALLOWED_IMAGE_EXTENSIONS = {".jpg", ".jpeg", ".png", ".webp"}
MAX_IMAGE_SIZE = 5 * 1024 * 1024


def _resolve_login_poster_path(url: str | None) -> Path | None:
    if not url or not url.startswith("/static/settings/"):
        return None
    filename = os.path.basename(url.replace("/static/settings/", "", 1))
    if not filename:
        return None
    return SETTINGS_UPLOAD_DIR / filename


def _delete_file_if_exists(path: Path | None) -> None:
    if path is None:
        return
    try:
        if path.exists() and path.is_file():
            path.unlink()
    except OSError:
        pass


def _cleanup_login_poster_files(active_url: str | None) -> None:
    active_name = _resolve_login_poster_path(active_url).name if active_url else None
    for file in SETTINGS_UPLOAD_DIR.glob("android_login_poster_*"):
        if active_name and file.name == active_name:
            continue
        _delete_file_if_exists(file)


def _upsert_setting(session: Session, key: str, value: str) -> None:
    setting = session.get(SystemSetting, key)
    if setting is None:
        session.add(SystemSetting(key=key, value=value))
        return
    setting.value = value
    session.add(setting)


def _validate_image_upload(file: UploadFile) -> tuple[bytes, str]:
    original_name = file.filename or "poster"
    safe_name = os.path.basename(original_name).replace("..", "")
    extension = Path(safe_name).suffix.lower()

    if extension not in ALLOWED_IMAGE_EXTENSIONS:
        raise HTTPException(status_code=400, detail="仅支持 JPG、PNG、WebP 格式图片")

    content = file.file.read()
    if not content:
        raise HTTPException(status_code=400, detail="上传文件不能为空")
    if len(content) > MAX_IMAGE_SIZE:
        raise HTTPException(status_code=400, detail="图片大小不能超过 5MB")

    return content, extension


@router.get("/", response_model=Dict[str, str])
def get_settings(session: Session = Depends(get_session)):
    settings = session.exec(select(SystemSetting)).all()
    return {setting.key: setting.value for setting in settings}


@router.post("/")
def update_settings(settings: Dict[str, str], session: Session = Depends(get_session)):
    visibility_setting_changed = False
    previous_poster_url = (session.get(SystemSetting, ANDROID_LOGIN_POSTER_URL_KEY) or SystemSetting(key="", value="")).value or None

    for key, value in settings.items():
        value_str = "" if value is None else str(value)
        setting = session.get(SystemSetting, key)
        if setting is None:
            session.add(SystemSetting(key=key, value=value_str))
            if key == ANDROID_VISIBILITY_SETTING_KEY:
                visibility_setting_changed = True
            continue

        if key == ANDROID_VISIBILITY_SETTING_KEY and setting.value != value_str:
            visibility_setting_changed = True

        setting.value = value_str
        session.add(setting)

    session.commit()
    current_poster_url = (session.get(SystemSetting, ANDROID_LOGIN_POSTER_URL_KEY) or SystemSetting(key="", value="")).value or None

    if previous_poster_url != current_poster_url:
        _delete_file_if_exists(_resolve_login_poster_path(previous_poster_url))
        _cleanup_login_poster_files(current_poster_url)

    if visibility_setting_changed:
        try:
            sio.start_background_task(
                broadcast_meeting_changed,
                "settings_updated",
                {"setting_key": ANDROID_VISIBILITY_SETTING_KEY}
            )
        except Exception as e:
            print(f"[Socket.IO] failed to broadcast settings_updated: {e}")

    return {"ok": True}


@router.post("/upload_login_poster")
def upload_login_poster(
    file: UploadFile = File(...),
    session: Session = Depends(get_session)
):
    content, extension = _validate_image_upload(file)
    version = str(int(datetime.now().timestamp()))
    filename = f"android_login_poster_{version}{extension}"
    file_path = SETTINGS_UPLOAD_DIR / filename

    with open(file_path, "wb") as output:
        output.write(content)

    poster_url = f"/static/settings/{filename}"
    _upsert_setting(session, ANDROID_LOGIN_POSTER_URL_KEY, poster_url)
    _upsert_setting(session, ANDROID_LOGIN_POSTER_VERSION_KEY, version)
    session.commit()
    _cleanup_login_poster_files(poster_url)

    return {
        "url": poster_url,
        "version": version
    }
