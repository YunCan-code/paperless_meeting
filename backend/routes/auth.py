from fastapi import APIRouter, Depends, HTTPException
from sqlmodel import Session, select
from pydantic import BaseModel
from typing import Optional
from database import get_session
from models import User

# 创建路由器，前缀为 /auth
router = APIRouter(prefix="/auth", tags=["auth"])

# 定义登录请求体结构
class LoginRequest(BaseModel):
    name: str

# 定义登录响应体结构
class LoginResponse(BaseModel):
    user_id: int
    name: str
    department: Optional[str] = None

@router.post("/login", response_model=LoginResponse)
def login(request: LoginRequest, session: Session = Depends(get_session)):
    """
    平板端登录接口
    简单实现：直接通过姓名匹配用户
    """
    statement = select(User).where(User.name == request.name)
    user = session.exec(statement).first()
    
    if not user:
        raise HTTPException(status_code=404, detail="User not found")
        
    return LoginResponse(
        user_id=user.id,
        name=user.name,
        department=user.department
    )
