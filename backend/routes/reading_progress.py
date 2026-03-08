from fastapi import APIRouter, Depends, HTTPException, Query
from sqlmodel import Session, select
from pydantic import BaseModel
from typing import List
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


class DeleteReadingProgressRequest(BaseModel):
    user_id: int
    file_url: str


def _delete_progress_entry(user_id: int, file_url: str, session: Session):
    entry = session.exec(
        select(ReadingProgress).where(
            ReadingProgress.user_id == user_id,
            ReadingProgress.file_url == file_url
        )
    ).first()

    if not entry:
        raise HTTPException(status_code=404, detail="Reading progress not found")

    session.delete(entry)
    session.commit()

    return {"message": "deleted"}


@router.post("/", response_model=ReadingProgressResponse)
def save_progress(req: ReadingProgressRequest, session: Session = Depends(get_session)):
    """保存或更新阅读进度（按 user_id + file_url upsert）"""
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
            file_url=item.file_url,
            file_name=item.file_name,
            current_page=item.current_page,
            total_pages=item.total_pages,
            updated_at=item.updated_at
        )
        for item in results
    ]


@router.delete("/{user_id}")
def delete_progress(
    user_id: int,
    file_url: str = Query(...),
    session: Session = Depends(get_session)
):
    return _delete_progress_entry(user_id=user_id, file_url=file_url, session=session)


@router.post("/delete")
def delete_progress_compat(
    req: DeleteReadingProgressRequest,
    session: Session = Depends(get_session)
):
    return _delete_progress_entry(user_id=req.user_id, file_url=req.file_url, session=session)
