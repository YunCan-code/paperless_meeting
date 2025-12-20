from fastapi import APIRouter, Depends, HTTPException, Query
from sqlmodel import Session, select, func
from typing import List, Optional
from database import get_session
from models import User, UserRead

# Create Router
router = APIRouter(prefix="/users", tags=["users"])

# Response Model using Pydantic Generic not supported directly in simple setup, so we return dict or specific schema
# For list with pagination, we can return { "items": [...], "total": ... }

@router.get("/stats")
def get_user_stats(session: Session = Depends(get_session)):
    """
    Get User Statistics
    """
    total = session.exec(select(func.count(User.id))).one()
    speakers = session.exec(select(func.count(User.id)).where(User.role == "主讲人")).one()
    attendees = session.exec(select(func.count(User.id)).where(User.role == "参会人员")).one()
    
    return {
        "total": total,
        "speakers": speakers,
        "attendees": attendees,
        "active_today": 45 # Mock for now as requested
    }

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
    query = select(User)
    
    # Filter: Search (Phone or Name)
    if q:
        query = query.where(
            (User.phone.contains(q)) | 
            (User.name.contains(q))
        )
    
    # Filter: Role
    if role:
        query = query.where(User.role == role)
        
    # Filter: Active Status
    if is_active is not None:
        query = query.where(User.is_active == is_active)
    
    # Total Count
    # Efficient count requires separate query or counting results
    total = len(session.exec(query).all())
    
    # Pagination
    query = query.offset((page - 1) * page_size).limit(page_size)
    query = query.order_by(User.id.desc())
    
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
        if key != 'id': # Prevent ID change
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
