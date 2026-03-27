from fastapi import APIRouter, Depends
from sqlmodel import Session, select
from typing import Dict

from database import get_session
from models import SystemSetting
from socket_manager import sio, broadcast_meeting_changed

router = APIRouter(prefix="/settings", tags=["settings"])

ANDROID_VISIBILITY_SETTING_KEY = "meeting_visibility_hide_after_hours"


@router.get("/", response_model=Dict[str, str])
def get_settings(session: Session = Depends(get_session)):
    """获取所有系统设置"""
    settings = session.exec(select(SystemSetting)).all()
    return {setting.key: setting.value for setting in settings}


@router.post("/")
def update_settings(settings: Dict[str, str], session: Session = Depends(get_session)):
    """批量更新系统设置"""
    visibility_setting_changed = False

    for key, value in settings.items():
        setting = session.get(SystemSetting, key)
        if not setting:
            setting = SystemSetting(key=key, value=value)
            session.add(setting)
            if key == ANDROID_VISIBILITY_SETTING_KEY:
                visibility_setting_changed = True
            continue

        if key == ANDROID_VISIBILITY_SETTING_KEY and setting.value != value:
            visibility_setting_changed = True

        setting.value = value
        session.add(setting)

    session.commit()

    if visibility_setting_changed:
        try:
            sio.start_background_task(
                broadcast_meeting_changed,
                "settings_updated",
                {
                    "setting_key": ANDROID_VISIBILITY_SETTING_KEY
                }
            )
        except Exception as e:
            print(f"[Socket.IO] failed to broadcast settings_updated: {e}")

    return {"ok": True}
