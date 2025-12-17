from typing import List, Optional
from datetime import datetime
from sqlmodel import Field, Relationship, SQLModel

# 会议和参会人员的多对多关联表
class MeetingAttendeeLink(SQLModel, table=True):
    meeting_id: Optional[int] = Field(default=None, foreign_key="meeting.id", primary_key=True)
    user_id: Optional[int] = Field(default=None, foreign_key="user.id", primary_key=True)

# 用户 (参会人员) 模型
class User(SQLModel, table=True):
    id: Optional[int] = Field(default=None, primary_key=True)
    name: str  # 姓名
    department: Optional[str] = None  # 部门
    position: Optional[str] = None    # 职位
    
    # 反向关联: 该用户参加的所有会议
    meetings: List["Meeting"] = Relationship(back_populates="attendees", link_model=MeetingAttendeeLink)

# 会议类型模型 (如: 党委会, 办公会)
class MeetingType(SQLModel, table=True):
    id: Optional[int] = Field(default=None, primary_key=True)
    name: str = Field(unique=True) # 类型名称，必须唯一
    description: Optional[str] = Field(default="~") # 描述，默认为~
    created_at: datetime = Field(default_factory=datetime.now) # 创建时间

# 会议模型
class Meeting(SQLModel, table=True):
    id: Optional[int] = Field(default=None, primary_key=True)
    title: str  # 会议标题
    meeting_type_id: Optional[int] = Field(default=None, foreign_key="meetingtype.id") # 关联会议类型
    start_time: datetime # 开始时间
    location: Optional[str] = None # 会议地点
    created_at: datetime = Field(default_factory=datetime.now) # 创建时间
    status: str = Field(default="scheduled") # 会议状态: scheduled(计划中), active(进行中), finished(已结束)

    # 关联属性
    attendees: List[User] = Relationship(back_populates="meetings", link_model=MeetingAttendeeLink) # 参会人员列表
    attachments: List["Attachment"] = Relationship(back_populates="meeting") # 附件列表

# 附件模型
class Attachment(SQLModel, table=True):
    id: Optional[int] = Field(default=None, primary_key=True)
    filename: str # 物理文件名（存储在磁盘上的唯一名称）
    display_name: str # 显示文件名（用户重命名后的名称）
    file_path: str # 本地存储路径
    file_size: int = Field(default=0) # 文件大小 (bytes)
    content_type: str = Field(default="application/octet-stream") # 文件类型
    sort_order: int = Field(default=0) # 排序权重
    meeting_id: Optional[int] = Field(default=None, foreign_key="meeting.id") # 所属会议
    uploaded_at: datetime = Field(default_factory=datetime.now) # 上传时间

    # 反向关联: 所属会议对象
    meeting: Optional[Meeting] = Relationship(back_populates="attachments")
