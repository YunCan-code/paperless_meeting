from fastapi import APIRouter, Depends, HTTPException
from sqlmodel import Session, select
from typing import List
from datetime import datetime

from database import get_session
from models import Note, NoteRead, NoteUpdate

router = APIRouter(prefix="/notes", tags=["notes"])

@router.post("/", response_model=NoteRead)
def create_note(note: Note, session: Session = Depends(get_session)):
    note.created_at = datetime.now()
    note.updated_at = datetime.now()
    session.add(note)
    session.commit()
    session.refresh(note)
    return note

@router.get("/", response_model=List[NoteRead])
def read_notes(skip: int = 0, limit: int = 100, session: Session = Depends(get_session)):
    notes = session.exec(select(Note).offset(skip).limit(limit).order_by(Note.created_at.desc())).all()
    return notes

@router.get("/{note_id}", response_model=NoteRead)
def read_note(note_id: int, session: Session = Depends(get_session)):
    note = session.get(Note, note_id)
    if not note:
        raise HTTPException(status_code=404, detail="Note not found")
    return note

@router.put("/{note_id}", response_model=NoteRead)
def update_note(note_id: int, note_update: NoteUpdate, session: Session = Depends(get_session)):
    db_note = session.get(Note, note_id)
    if not db_note:
        raise HTTPException(status_code=404, detail="Note not found")
    
    note_data = note_update.model_dump(exclude_unset=True)
    for key, value in note_data.items():
        setattr(db_note, key, value)
    
    db_note.updated_at = datetime.now()
    session.add(db_note)
    session.commit()
    session.refresh(db_note)
    return db_note

@router.delete("/{note_id}")
def delete_note(note_id: int, session: Session = Depends(get_session)):
    note = session.get(Note, note_id)
    if not note:
        raise HTTPException(status_code=404, detail="Note not found")
    session.delete(note)
    session.commit()
    return {"ok": True}
