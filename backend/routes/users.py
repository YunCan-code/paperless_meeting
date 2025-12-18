from fastapi import APIRouter, Depends, HTTPException
from sqlmodel import Session, select
from typing import List
from database import get_session
from models import User, UserRead

# 创建路由器，前缀为 /users
router = APIRouter(prefix="/users", tags=["users"])

@router.post("/", response_model=UserRead)
def create_user(user: User, session: Session = Depends(get_session)):
    """
    创建新用户
    """
    session.add(user)
    session.commit()
    session.refresh(user)
    return user

@router.get("/", response_model=List[UserRead])
def read_users(session: Session = Depends(get_session)):
    """
    获取所有用户列表
    """
    users = session.exec(select(User)).all()
    return users

@router.delete("/{user_id}")
def delete_user(user_id: int, session: Session = Depends(get_session)):
    """
    根据 ID 删除用户
    """
    user = session.get(User, user_id)
    if not user:
        raise HTTPException(status_code=404, detail="User not found")
    session.delete(user)
    session.commit()
    return {"ok": True}
