from fastapi import APIRouter, Depends, HTTPException
from sqlmodel import Session, select
from pydantic import BaseModel
from typing import Optional
from database import get_session
from models import User

from datetime import datetime

# 创建路由器，前缀为 /auth
router = APIRouter(prefix="/auth", tags=["auth"])

# 定义登录请求体结构
class LoginRequest(BaseModel):
    query: str # 姓名或手机号

# 定义登录响应体结构
class LoginResponse(BaseModel):
    user_id: int
    name: str
    department: Optional[str] = None
    token: str = "mock-token" # 简单模拟 token

@router.post("/login", response_model=LoginResponse)
def login(request: LoginRequest, session: Session = Depends(get_session)):
    """
    平板端登录接口
    支持通过姓名或手机号登录
    如果姓名重复，则返回错误提示用户使用手机号
    """
    q = request.query.strip()
    if not q:
        raise HTTPException(status_code=400, detail="请输入姓名或手机号")

    # 查找匹配的用户 (姓名或手机号)
    statement = select(User).where((User.name == q) | (User.phone == q))
    results = session.exec(statement).all()
    
    if not results:
        raise HTTPException(status_code=404, detail="未找到该用户")

    if len(results) > 1:
        # 如果有多个人匹配 (通常是重名)
        # 检查是否有完全匹配手机号的 (手机号理论上唯一)
        exact_phone = [u for u in results if u.phone == q]
        if len(exact_phone) == 1:
            user = exact_phone[0]
        else:
            # 都是重名且输入的不是手机号 OR 手机号也重复(数据错误)
            raise HTTPException(
                status_code=300, 
                detail=f"存在重名用户 '{q}'，请使用手机号登录"
            )
    else:
        # 只有一个匹配
        user = results[0]

    # Update last_login
    user.last_login = datetime.now()
    session.add(user)
    session.commit()
    session.refresh(user)

    return LoginResponse(
        user_id=user.id,
        name=user.name,
        department=user.department,
        token="demo-token-12345"
    )
