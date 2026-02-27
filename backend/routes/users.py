from fastapi import APIRouter, Depends, HTTPException, Query, UploadFile, File
from fastapi.responses import StreamingResponse
from sqlmodel import Session, select, func
from typing import List, Optional
import io
import openpyxl
from urllib.parse import quote
from database import get_session
from models import User, UserRead
from utils.security import hash_password, verify_password

# Create Router
router = APIRouter(prefix="/users", tags=["users"])

# Response Model using Pydantic Generic not supported directly in simple setup, so we return dict or specific schema
# For list with pagination, we can return { "items": [...], "total": ... }

from pydantic import BaseModel

class ChangePasswordRequest(BaseModel):
    user_id: int
    old_password: str
    new_password: str

@router.get("/stats")
def get_user_stats(session: Session = Depends(get_session)):
    """
    Get User Statistics
    """
    total = session.exec(select(func.count(User.id))).one()
    speakers = session.exec(select(func.count(User.id)).where(User.role == "主讲人")).one()
    attendees = session.exec(select(func.count(User.id)).where(User.role == "参会人员")).one()
    
    # Calculate active_today
    # Logic: last_login >= today 00:00
    from datetime import datetime
    now = datetime.now()
    today_start = datetime(now.year, now.month, now.day)
    
    active_today = session.exec(select(func.count(User.id)).where(User.last_login >= today_start)).one()
    
    return {
        "total": total,
        "speakers": speakers,
        "attendees": attendees,
        "active_today": active_today
    }

@router.get("/template")
def download_template():
    """
    Download User Import Template
    """
    wb = openpyxl.Workbook()
    ws = wb.active
    ws.title = "用户导入模板"
    # Headers
    headers = ["姓名", "联系方式", "电子邮箱", "所属区县", "所属部门", "系统角色(主讲人/参会人员)"]
    ws.append(headers)
    
    # Sample Row
    ws.append(["张三", "13800138000", "zhangsan@example.com", "五华区", "研发部", "参会人员"])
    
    # Adjust column width
    for col in range(1, len(headers)+1):
        ws.column_dimensions[openpyxl.utils.get_column_letter(col)].width = 20

    output = io.BytesIO()
    wb.save(output)
    output.seek(0)
    
    filename = quote("用户导入模板.xlsx")
    return StreamingResponse(
        output, 
        media_type="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
        headers={"Content-Disposition": f"attachment; filename={filename}; filename*=utf-8''{filename}"}
    )

@router.post("/import")
async def import_users(file: UploadFile = File(...), session: Session = Depends(get_session)):
    """
    Import Users from Excel
    """
    if not file.filename.endswith('.xlsx'):
        raise HTTPException(status_code=400, detail="Invalid file format. Please upload .xlsx file")

    try:
        content = await file.read()
        wb = openpyxl.load_workbook(io.BytesIO(content))
        ws = wb.active

        users_to_add = []
        errors = []
        seen_phones = {}  # 文件内手机号去重: phone -> 行号

        # Skip header, iterate rows
        for i, row in enumerate(ws.iter_rows(min_row=2, values_only=True), start=2):
            # Row: Name, Phone, Email, District, Dept, Role
            name, phone, email, district, dept, role = row[0:6]

            if not name: continue  # Skip empty rows

            # 手机号查重
            phone_str = str(phone).strip() if phone else None

            if phone_str:
                # 文件内互查
                if phone_str in seen_phones:
                    errors.append(f"第 {i} 行: 手机号 '{phone_str}' 与第 {seen_phones[phone_str]} 行重复")
                    continue
                seen_phones[phone_str] = i

                # 与数据库比对
                existing = session.exec(select(User).where(User.phone == phone_str)).first()
                if existing:
                    errors.append(f"第 {i} 行: 手机号 '{phone_str}' 已被用户 '{existing.name}' 使用")
                    continue

            # Role validation
            if role not in ["主讲人", "参会人员"]:
                role = "参会人员"  # Default

            user = User(
                name=str(name),
                phone=phone_str,
                email=str(email) if email else None,
                district=str(district) if district else None,
                department=str(dept) if dept else None,
                role=str(role),
                is_active=True,
                password=hash_password("password123")  # 默认密码哈希存储
            )
            users_to_add.append(user)

        if errors:
            raise HTTPException(status_code=400, detail={
                "message": f"导入失败，存在 {len(errors)} 个冲突",
                "errors": errors
            })

        session.add_all(users_to_add)
        session.commit()

        return {"count": len(users_to_add), "message": f"Successfully imported {len(users_to_add)} users"}

    except HTTPException:
        raise
    except Exception as e:
        raise HTTPException(status_code=400, detail=f"Import failed: {str(e)}")

@router.post("/", response_model=UserRead)
def create_user(user: User, session: Session = Depends(get_session)):
    """
    Create New User
    """
    # Check if phone exists (optional)
    if user.phone:
        existing = session.exec(select(User).where(User.phone == user.phone)).first()
        if existing:
            raise HTTPException(status_code=400, detail="Phone already registered")

    # 对密码进行哈希存储
    if user.password:
        user.password = hash_password(user.password)

    session.add(user)
    session.commit()
    session.refresh(user)
    return user

@router.get("/")
def read_users(
    session: Session = Depends(get_session),
    page: int = 1,
    page_size: int = 10,
    q: Optional[str] = None,
    role: Optional[str] = None,
    is_active: Optional[bool] = None
):
    """
    Get Users List with Pagination and Filtering
    """
    
    # Efficient Count
    # We clone the query conditions for counting
    count_query = select(func.count()).select_from(User)
    if q:
        count_query = count_query.where((User.phone.contains(q)) | (User.name.contains(q)))
    if role:
        count_query = count_query.where(User.role == role)
    if is_active is not None:
        count_query = count_query.where(User.is_active == is_active)
        
    total = session.exec(count_query).one()
    
    # Build Result Query
    query = select(User)
    if q:
        query = query.where((User.phone.contains(q)) | (User.name.contains(q)))
    if role:
        query = query.where(User.role == role)
    if is_active is not None:
        query = query.where(User.is_active == is_active)
        
    query = query.offset((page - 1) * page_size).limit(page_size).order_by(User.id.desc())
    items = session.exec(query).all()
    
    return {
        "items": items,
        "total": total,
        "page": page,
        "page_size": page_size
    }

@router.get("/{user_id}", response_model=UserRead)
def read_user(user_id: int, session: Session = Depends(get_session)):
    user = session.get(User, user_id)
    if not user:
        raise HTTPException(status_code=404, detail="User not found")
    return user

@router.put("/{user_id}", response_model=UserRead)
def update_user(user_id: int, user_data: User, session: Session = Depends(get_session)):
    user = session.get(User, user_id)
    if not user:
        raise HTTPException(status_code=404, detail="User not found")

    # Update fields
    user_data_dict = user_data.dict(exclude_unset=True)
    for key, value in user_data_dict.items():
        if key != 'id':  # Prevent ID change
            # 如果更新密码，且不是已哈希的值，则做哈希
            if key == 'password' and value and not value.startswith("$2b$"):
                value = hash_password(value)
            setattr(user, key, value)

    session.add(user)
    session.commit()
    session.refresh(user)
    return user

@router.delete("/{user_id}")
def delete_user(user_id: int, session: Session = Depends(get_session)):
    """
    Delete User by ID
    """
    user = session.get(User, user_id)
    if not user:
        raise HTTPException(status_code=404, detail="User not found")
    session.delete(user)
    session.commit()
    return {"ok": True}

@router.post("/change_password")
def change_password(req: ChangePasswordRequest, session: Session = Depends(get_session)):
    """
    修改用户密码
    """
    user = session.get(User, req.user_id)
    if not user:
        raise HTTPException(status_code=404, detail="User not found")

    # 验证旧密码（兼容明文旧密码和 bcrypt 哈希）
    current_pwd = user.password or "password123"  # Fallback to default if null
    if not verify_password(req.old_password, current_pwd):
        raise HTTPException(status_code=400, detail="旧密码错误")

    # 新密码使用哈希存储
    user.password = hash_password(req.new_password)
    session.add(user)
    session.commit()

    return {"message": "密码修改成功"}
