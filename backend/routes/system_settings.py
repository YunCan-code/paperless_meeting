from fastapi import APIRouter, Depends, HTTPException
from sqlmodel import Session, select
from typing import Dict, Any, List
from database import get_session
from models import SystemSetting

router = APIRouter(prefix="/settings", tags=["settings"])

@router.get("/", response_model=Dict[str, str])
def get_settings(session: Session = Depends(get_session)):
    """获取所有系统设置"""
    settings = session.exec(select(SystemSetting)).all()
    return {s.key: s.value for s in settings}

@router.post("/")
def update_settings(settings: Dict[str, str], session: Session = Depends(get_session)):
    """批量更新系统设置"""
    for key, value in settings.items():
        setting = session.get(SystemSetting, key)
        if not setting:
            setting = SystemSetting(key=key, value=value)
            session.add(setting)
        else:
            setting.value = value
            session.add(setting)
    
    session.commit()
    return {"ok": True}
