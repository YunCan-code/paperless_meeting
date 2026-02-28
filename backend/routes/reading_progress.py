from fastapi import APIRouter, Depends
from sqlmodel import Session, select
from pydantic import BaseModel
from typing import List, Optional
from datetime import datetime
from database import get_session
from models import ReadingProgress

router = APIRouter(prefix="/reading-progress", tags=["Reading Progress"])


class ReadingProgressRequest(BaseModel):
    user_id: int
    file_url: str
    file_name: str
    current_page: int
    total_pages: int


class ReadingProgressResponse(BaseModel):
    file_url: str
    file_name: str
    current_page: int
    total_pages: int
    updated_at: datetime


@router.post("/", response_model=ReadingProgressResponse)
def save_progress(req: ReadingProgressRequest, session: Session = Depends(get_session)):
    """保存或更新阅读进度 (upsert by user_id + file_url)"""
    existing = session.exec(
        select(ReadingProgress).where(
            ReadingProgress.user_id == req.user_id,
            ReadingProgress.file_url == req.file_url
        )
    ).first()

    if existing:
        existing.current_page = req.current_page
        existing.total_pages = req.total_pages
        existing.file_name = req.file_name
        existing.updated_at = datetime.now()
        session.add(existing)
    else:
        entry = ReadingProgress(
            user_id=req.user_id,
            file_url=req.file_url,
            file_name=req.file_name,
            current_page=req.current_page,
            total_pages=req.total_pages,
            updated_at=datetime.now()
        )
        session.add(entry)

    session.commit()

    result = session.exec(
        select(ReadingProgress).where(
            ReadingProgress.user_id == req.user_id,
            ReadingProgress.file_url == req.file_url
        )
    ).first()

    return ReadingProgressResponse(
        file_url=result.file_url,
        file_name=result.file_name,
        current_page=result.current_page,
        total_pages=result.total_pages,
        updated_at=result.updated_at
    )


@router.get("/{user_id}", response_model=List[ReadingProgressResponse])
def get_progress(user_id: int, session: Session = Depends(get_session)):
    """获取指定用户的阅读进度列表（按更新时间倒序，最多 20 条）"""
    results = session.exec(
        select(ReadingProgress)
        .where(ReadingProgress.user_id == user_id)
        .order_by(ReadingProgress.updated_at.desc())
        .limit(20)
    ).all()

    return [
        ReadingProgressResponse(
            file_url=r.file_url,
            file_name=r.file_name,
            current_page=r.current_page,
            total_pages=r.total_pages,
            updated_at=r.updated_at
        )
        for r in results
    ]
