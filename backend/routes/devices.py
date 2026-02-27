from fastapi import APIRouter, Depends, HTTPException, Query, Request
from sqlmodel import Session, select, SQLModel
from typing import List, Optional
from datetime import datetime

from database import get_session
from models import Device, DeviceRead, DeviceBase

router = APIRouter(prefix="/devices", tags=["devices"])

@router.post("/heartbeat", response_model=DeviceRead)
async def device_heartbeat(
    device_data: DeviceBase, 
    request: Request,
    session: Session = Depends(get_session)
):
    """
    设备心跳上报。如果设备不存在则创建，存在则更新状态。
    """
    statement = select(Device).where(Device.device_id == device_data.device_id)
    existing_device = session.exec(statement).first()
    
    # 获取IP地址: 优先使用客户端上报的局域网IP，如果没有则使用连接IP
    client_ip = request.client.host
    if "x-forwarded-for" in request.headers:
        # 取最左侧的第一个 IP，即客户端真实 IP（信任链依赖 Nginx 正确配置 proxy_set_header）
        client_ip = request.headers["x-forwarded-for"].split(",")[0].strip()

    final_ip = device_data.ip_address if device_data.ip_address else client_ip

    if existing_device:
        existing_device.last_active_at = datetime.now()
        existing_device.ip_address = final_ip
        # 更新其他可能变化的信息
        if device_data.app_version:
            existing_device.app_version = device_data.app_version
        if device_data.os_version:
            existing_device.os_version = device_data.os_version
        if device_data.model:
            existing_device.model = device_data.model
        if device_data.name:
            existing_device.name = device_data.name
        
        # Update telemetry
        existing_device.mac_address = device_data.mac_address
        existing_device.battery_level = device_data.battery_level
        existing_device.is_charging = device_data.is_charging
        existing_device.storage_total = device_data.storage_total
        existing_device.storage_available = device_data.storage_available
            
        session.add(existing_device)
        session.commit()
        session.refresh(existing_device)
        return existing_device
    else:
        new_device = Device.from_orm(device_data)
        new_device.ip_address = final_ip
        new_device.last_active_at = datetime.now()
        session.add(new_device)
        session.commit()
        session.refresh(new_device)
        return new_device

@router.get("/", response_model=List[DeviceRead])
async def list_devices(
    skip: int = 0, 
    limit: int = 100, 
    filter_active: bool = False,
    session: Session = Depends(get_session)
):
    query = select(Device).order_by(Device.last_active_at.desc())
    if filter_active:
        # 简单过滤: 状态为 active 且最近活跃? 暂时只看 status 字段
        query = query.where(Device.status == "active")
        
    devices = session.exec(query.offset(skip).limit(limit)).all()
    return devices

@router.delete("/{device_id}")
async def delete_device(device_id: int, session: Session = Depends(get_session)):
    device = session.get(Device, device_id)
    if not device:
        raise HTTPException(status_code=404, detail="Device not found")
    session.delete(device)
    session.commit()
    return {"ok": True}

@router.put("/{device_id}/block")
async def block_device(device_id: int, session: Session = Depends(get_session)):
    device = session.get(Device, device_id)
    if not device:
        raise HTTPException(status_code=404, detail="Device not found")
    device.status = "blocked"
    session.add(device)
    session.commit()
    return {"ok": True}

class DeviceUpdate(SQLModel):
    alias: Optional[str] = None
    name: Optional[str] = None

@router.put("/{device_id}", response_model=DeviceRead)
async def update_device(
    device_id: int, 
    device_update: DeviceUpdate,
    session: Session = Depends(get_session)
):
    device = session.get(Device, device_id)
    if not device:
        raise HTTPException(status_code=404, detail="Device not found")
    
    if device_update.alias is not None:
        device.alias = device_update.alias
        
    session.add(device)
    session.commit()
    session.refresh(device)
    return device

@router.put("/{device_id}/unblock")
async def unblock_device(device_id: int, session: Session = Depends(get_session)):
    device = session.get(Device, device_id)
    if not device:
        raise HTTPException(status_code=404, detail="Device not found")
    device.status = "active"
    session.add(device)
    session.commit()
    session.refresh(device)
    return {"ok": True}

# ============= 设备指令相关接口 =============
from models import DeviceCommand, DeviceCommandRead
from pydantic import BaseModel

class BatchCommandRequest(BaseModel):
    device_ids: List[str]  # 设备ID列表
    command_type: str  # "update_app", "restart"
    payload: Optional[str] = None

@router.post("/commands", response_model=List[DeviceCommandRead])
async def send_batch_commands(
    request: BatchCommandRequest,
    session: Session = Depends(get_session)
):
    """批量向设备发送指令"""
    commands = []
    for device_id in request.device_ids:
        cmd = DeviceCommand(
            device_id=device_id,
            command_type=request.command_type,
            payload=request.payload
        )
        session.add(cmd)
        commands.append(cmd)
    session.commit()
    for cmd in commands:
        session.refresh(cmd)
    return commands

@router.get("/{device_id}/commands", response_model=List[DeviceCommandRead])
async def get_device_commands(
    device_id: str,
    session: Session = Depends(get_session)
):
    """设备查询待执行的指令"""
    statement = select(DeviceCommand).where(
        DeviceCommand.device_id == device_id,
        DeviceCommand.status == "pending"
    ).order_by(DeviceCommand.created_at.asc())
    commands = session.exec(statement).all()
    return commands

@router.put("/commands/{command_id}/ack")
async def ack_command(
    command_id: int,
    session: Session = Depends(get_session)
):
    """设备确认执行完成"""
    cmd = session.get(DeviceCommand, command_id)
    if not cmd:
        raise HTTPException(status_code=404, detail="Command not found")
    cmd.status = "acked"
    cmd.acked_at = datetime.now()
    session.add(cmd)
    session.commit()
    return {"ok": True}

