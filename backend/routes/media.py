from fastapi import APIRouter, Depends, HTTPException, UploadFile, File, Form
from fastapi.responses import JSONResponse
from sqlmodel import Session, select
from sqlalchemy import func
from typing import List, Optional, Dict, Union
from pathlib import Path
from datetime import datetime
import uuid
import os

from database import get_session
from models import MediaItem, MediaItemRead, MediaItemPage, MediaItemUpdate, MediaItemMove

router = APIRouter(prefix="/media", tags=["media"])

MEDIA_UPLOAD_DIR = Path("uploads/media")
MEDIA_UPLOAD_DIR.mkdir(parents=True, exist_ok=True)

MEDIA_THUMB_DIR = Path("uploads/thumbnails/media")
MEDIA_THUMB_DIR.mkdir(parents=True, exist_ok=True)

MEDIA_THUMB_SIZE = (480, 480)
MEDIA_THUMB_QUALITY = 72

try:
    from PIL import Image as PILImage, ImageOps  # type: ignore
    PIL_AVAILABLE = True
except Exception:
    PIL_AVAILABLE = False

ALLOWED_IMAGE_TYPES = {"image/jpeg", "image/png", "image/gif", "image/webp", "image/bmp", "image/svg+xml"}
ALLOWED_VIDEO_TYPES = {"video/mp4", "video/quicktime", "video/x-msvideo", "video/webm", "video/x-matroska"}
ALLOWED_TYPES = ALLOWED_IMAGE_TYPES | ALLOWED_VIDEO_TYPES


def _format_size(size: int) -> str:
    if size >= 1024 * 1024 * 1024:
        return f"{size / (1024 * 1024 * 1024):.1f} GB"
    if size >= 1024 * 1024:
        return f"{size / (1024 * 1024):.1f} MB"
    if size >= 1024:
        return f"{round(size / 1024)} KB"
    return f"{size} B"


def _build_media_thumbnail(source_path: Path) -> str:
    """Build (or reuse) WebP thumbnail for a media image. Returns URL path or empty string."""
    if not PIL_AVAILABLE:
        return ""
    try:
        source = Path(source_path)
        if not source.is_file():
            return ""
        stem = source.stem
        ext = source.suffix.lower().lstrip(".") or "img"
        w, h = MEDIA_THUMB_SIZE
        thumb_name = f"{stem}_{ext}_{w}x{h}.webp"
        thumb_path = MEDIA_THUMB_DIR / thumb_name

        source_mtime = source.stat().st_mtime
        if thumb_path.exists() and thumb_path.stat().st_mtime >= source_mtime:
            return f"/static/thumbnails/media/{thumb_name}"

        resample = PILImage.Resampling.LANCZOS if hasattr(PILImage, "Resampling") else PILImage.LANCZOS
        with PILImage.open(source) as img:
            img = ImageOps.exif_transpose(img).convert("RGB")
            fitted = ImageOps.fit(img, MEDIA_THUMB_SIZE, method=resample)
            fitted.save(thumb_path, format="WEBP", quality=MEDIA_THUMB_QUALITY, method=6)

        return f"/static/thumbnails/media/{thumb_name}"
    except Exception as e:
        print(f"[media-thumb] failed to build thumbnail for {source_path}: {e}")
        return ""


def _to_read(
    item: MediaItem,
    session: Session,
    children_counts: Optional[Dict[int, int]] = None,
) -> MediaItemRead:
    preview = ""
    thumbnail = ""
    if item.kind == "image" and item.filename:
        preview = f"/static/media/{item.filename}"
        thumbnail = _build_media_thumbnail(MEDIA_UPLOAD_DIR / item.filename)
    children_count = 0
    if item.kind == "folder":
        if children_counts is not None and item.id is not None:
            children_count = children_counts.get(item.id, 0)
        else:
            children_count = session.exec(
                select(func.count()).where(MediaItem.parent_id == item.id)
            ).one()
    return MediaItemRead(
        id=item.id,
        kind=item.kind,
        title=item.title,
        parent_id=item.parent_id,
        extension=item.extension,
        file_size=item.file_size,
        visible_on_android=item.visible_on_android,
        created_at=item.created_at,
        updated_at=item.updated_at,
        size=_format_size(item.file_size) if item.kind != "folder" else "",
        previewUrl=preview,
        thumbnailUrl=thumbnail,
        children_count=children_count,
    )


# ---------- 列表 ----------

@router.get("/items")
def list_items(
    parent_id: Optional[int] = None,
    kind: Optional[str] = None,
    visible_on_android: Optional[bool] = None,
    skip: int = 0,
    limit: int = 0,
    session: Session = Depends(get_session),
) -> Union[List[MediaItemRead], MediaItemPage]:
    stmt = select(MediaItem).where(MediaItem.parent_id == parent_id)
    if kind and kind != "all":
        stmt = stmt.where(MediaItem.kind == kind)
    if visible_on_android is not None:
        stmt = stmt.where(MediaItem.visible_on_android == visible_on_android)
    stmt = stmt.order_by(MediaItem.kind, MediaItem.updated_at.desc())

    # Total count (needed for pagination)
    if limit > 0:
        count_stmt = select(func.count()).select_from(stmt.subquery())
        total = session.exec(count_stmt).one()
        stmt = stmt.offset(skip).limit(limit)
    else:
        total = 0

    items = session.exec(stmt).all()

    # Batch children count for folders (fix N+1)
    folder_ids = [i.id for i in items if i.kind == "folder" and i.id is not None]
    children_counts: Dict[int, int] = {}
    if folder_ids:
        rows = session.exec(
            select(MediaItem.parent_id, func.count())
            .where(MediaItem.parent_id.in_(folder_ids))
            .group_by(MediaItem.parent_id)
        ).all()
        children_counts = {pid: cnt for pid, cnt in rows}

    result = [_to_read(i, session, children_counts) for i in items]

    if limit > 0:
        return MediaItemPage(items=result, total=total, skip=skip, limit=limit)
    return result


# ---------- 单项详情 ----------

@router.get("/items/{item_id}", response_model=MediaItemRead)
def get_item(item_id: int, session: Session = Depends(get_session)):
    item = session.get(MediaItem, item_id)
    if not item:
        raise HTTPException(404, "媒体项不存在")
    return _to_read(item, session)


# ---------- 文件夹树 ----------

@router.get("/tree")
def get_folder_tree(
    exclude_id: Optional[int] = None,
    session: Session = Depends(get_session),
):
    folders = session.exec(
        select(MediaItem).where(MediaItem.kind == "folder")
    ).all()

    excluded_ids = set()
    if exclude_id is not None:
        excluded_ids.add(exclude_id)
        _collect_descendant_ids(exclude_id, folders, excluded_ids)

    by_parent: dict[Optional[int], list] = {}
    for f in folders:
        if f.id in excluded_ids:
            continue
        by_parent.setdefault(f.parent_id, []).append(f)

    def build(parent_id: Optional[int]):
        nodes = []
        for f in by_parent.get(parent_id, []):
            nodes.append({
                "id": f.id,
                "title": f.title,
                "children": build(f.id),
            })
        return nodes

    return [{"id": 0, "title": "媒体库（根目录）", "children": build(None)}]


def _collect_descendant_ids(parent_id: int, all_folders, result_set: set):
    for f in all_folders:
        if f.parent_id == parent_id and f.id not in result_set:
            result_set.add(f.id)
            _collect_descendant_ids(f.id, all_folders, result_set)


# ---------- 面包屑 ----------

@router.get("/ancestors/{item_id}")
def get_ancestors(item_id: int, session: Session = Depends(get_session)):
    path = []
    current_id = item_id
    visited = set()
    while current_id is not None:
        if current_id in visited:
            break
        visited.add(current_id)
        item = session.get(MediaItem, current_id)
        if not item:
            break
        path.append({"id": item.id, "title": item.title})
        current_id = item.parent_id
    path.reverse()
    return path


# ---------- 创建文件夹 ----------

@router.post("/folders", response_model=MediaItemRead)
def create_folder(
    title: str = Form(...),
    parent_id: Optional[int] = Form(None),
    session: Session = Depends(get_session),
):
    if parent_id is not None:
        parent = session.get(MediaItem, parent_id)
        if not parent or parent.kind != "folder":
            raise HTTPException(400, "目标父文件夹不存在")

    folder = MediaItem(kind="folder", title=title.strip(), parent_id=parent_id)
    session.add(folder)
    session.commit()
    session.refresh(folder)
    return _to_read(folder, session)


# ---------- 上传文件 ----------

@router.post("/upload", response_model=List[MediaItemRead])
def upload_files(
    files: List[UploadFile] = File(...),
    parent_id: Optional[int] = Form(None),
    session: Session = Depends(get_session),
):
    if parent_id is not None:
        parent = session.get(MediaItem, parent_id)
        if not parent or parent.kind != "folder":
            raise HTTPException(400, "目标父文件夹不存在")

    created = []
    for f in files:
        if f.content_type not in ALLOWED_TYPES:
            continue

        ext = ""
        if f.filename and "." in f.filename:
            ext = f.filename.rsplit(".", 1)[-1].upper()
        display_name = f.filename.rsplit(".", 1)[0] if f.filename and "." in f.filename else (f.filename or "未命名")

        unique_name = f"{uuid.uuid4().hex}.{ext.lower()}" if ext else f"{uuid.uuid4().hex}"
        save_path = MEDIA_UPLOAD_DIR / unique_name

        content = f.file.read()
        with open(save_path, "wb") as out:
            out.write(content)

        kind = "image" if f.content_type in ALLOWED_IMAGE_TYPES else "video"
        item = MediaItem(
            kind=kind,
            title=display_name,
            parent_id=parent_id,
            filename=unique_name,
            file_path=str(save_path),
            file_size=len(content),
            content_type=f.content_type,
            extension=ext or None,
        )
        session.add(item)
        session.commit()
        session.refresh(item)

        # Pre-generate thumbnail for images at upload time
        if kind == "image":
            _build_media_thumbnail(save_path)

        created.append(_to_read(item, session))

    if not created:
        raise HTTPException(400, "没有支持的文件被上传（仅支持图片和视频）")
    return created


# ---------- 更新属性 ----------

@router.patch("/items/{item_id}", response_model=MediaItemRead)
def update_item(
    item_id: int,
    data: MediaItemUpdate,
    session: Session = Depends(get_session),
):
    item = session.get(MediaItem, item_id)
    if not item:
        raise HTTPException(404, "媒体项不存在")

    if data.title is not None:
        item.title = data.title.strip()
    if data.visible_on_android is not None:
        item.visible_on_android = data.visible_on_android
    item.updated_at = datetime.now()

    session.add(item)
    session.commit()
    session.refresh(item)
    return _to_read(item, session)


# ---------- 移动 ----------

@router.patch("/items/{item_id}/move", response_model=MediaItemRead)
def move_item(
    item_id: int,
    data: MediaItemMove,
    session: Session = Depends(get_session),
):
    item = session.get(MediaItem, item_id)
    if not item:
        raise HTTPException(404, "媒体项不存在")

    target_id = data.parent_id
    if target_id is not None:
        if target_id == item_id:
            raise HTTPException(400, "不能移动到自身")
        target = session.get(MediaItem, target_id)
        if not target or target.kind != "folder":
            raise HTTPException(400, "目标文件夹不存在")
        if item.kind == "folder":
            all_folders = session.exec(select(MediaItem).where(MediaItem.kind == "folder")).all()
            desc = set()
            _collect_descendant_ids(item_id, all_folders, desc)
            if target_id in desc:
                raise HTTPException(400, "不能移动到自身的子文件夹中")

    item.parent_id = target_id
    item.updated_at = datetime.now()
    session.add(item)
    session.commit()
    session.refresh(item)
    return _to_read(item, session)


# ---------- 删除 ----------

@router.delete("/items/{item_id}")
def delete_item(item_id: int, session: Session = Depends(get_session)):
    item = session.get(MediaItem, item_id)
    if not item:
        raise HTTPException(404, "媒体项不存在")

    _recursive_delete(item, session)
    session.commit()
    return {"ok": True}


def _recursive_delete(item: MediaItem, session: Session):
    if item.kind == "folder":
        children = session.exec(
            select(MediaItem).where(MediaItem.parent_id == item.id)
        ).all()
        for child in children:
            _recursive_delete(child, session)

    if item.filename:
        file_path = MEDIA_UPLOAD_DIR / item.filename
        if file_path.exists():
            try:
                os.remove(file_path)
            except OSError:
                pass

    session.delete(item)
