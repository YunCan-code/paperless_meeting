from typing import List, Optional
from datetime import datetime
from sqlmodel import Field, Relationship, SQLModel

# 会议和参会人员的多对多关联表
class MeetingAttendeeLink(SQLModel, table=True):
    meeting_id: Optional[int] = Field(default=None, foreign_key="meeting.id", primary_key=True)
    user_id: Optional[int] = Field(default=None, foreign_key="user.id", primary_key=True)

# 用户 (参会人员) 模型
class UserBase(SQLModel):
    name: str # 姓名 (Real Name)

    email: Optional[str] = None
    phone: Optional[str] = None
    district: Optional[str] = None # 区县
    department: Optional[str] = None # 部门
    position: Optional[str] = None # 职位
    role: str = Field(default="参会人员") # 角色: 主讲人/参会人员
    is_active: bool = Field(default=True) # 状态
    password: Optional[str] = None # 密码 (Demo purposes: stored plain text for 'click to show')
    last_login: Optional[datetime] = None

class User(UserBase, table=True):
    id: Optional[int] = Field(default=None, primary_key=True)
    
    # 反向关联: 该用户参加的所有会议
    meetings: List["Meeting"] = Relationship(back_populates="attendees", link_model=MeetingAttendeeLink)

class UserRead(UserBase):
    id: int

# 会议类型模型 (如: 党委会, 办公会)
class MeetingTypeBase(SQLModel):
    name: str = Field(unique=True) # 类型名称，必须唯一
    description: Optional[str] = Field(default="~") # 描述，默认为~
    is_fixed_image: bool = Field(default=False) # 是否使用固定封面
    cover_image: Optional[str] = None # 固定封面图片的 URL (或者文件名)

class MeetingType(MeetingTypeBase, table=True):
    id: Optional[int] = Field(default=None, primary_key=True)
    created_at: datetime = Field(default_factory=datetime.now) # 创建时间

class MeetingTypeRead(MeetingTypeBase):
    id: int
    created_at: datetime

# 附件模型
class AttachmentBase(SQLModel):
    filename: str # 物理文件名（存储在磁盘上的唯一名称）
    display_name: str # 显示文件名（用户重命名后的名称）
    file_path: str # 本地存储路径
    file_size: int = Field(default=0) # 文件大小 (bytes)
    content_type: str = Field(default="application/octet-stream") # 文件类型
    sort_order: int = Field(default=0) # 排序权重
    meeting_id: Optional[int] = Field(default=None, foreign_key="meeting.id") # 所属会议

class Attachment(AttachmentBase, table=True):
    id: Optional[int] = Field(default=None, primary_key=True)
    uploaded_at: datetime = Field(default_factory=datetime.now) # 上传时间

    # 反向关联: 所属会议对象
    meeting: Optional["Meeting"] = Relationship(back_populates="attachments")

class AttachmentRead(AttachmentBase):
    id: int
    uploaded_at: datetime

# 会议模型
class MeetingBase(SQLModel):
    title: str # 会议标题
    meeting_type_id: Optional[int] = Field(default=None, foreign_key="meetingtype.id") # 关联会议类型
    start_time: datetime # 开始时间
    location: Optional[str] = None # 会议地点
    speaker: Optional[str] = None # 主讲人
    agenda: Optional[str] = None # 议程 (JSON format: [{"time": "10:00", "content": "Intro"}, ...])
    status: str = Field(default="scheduled") # 会议状态: scheduled(计划中), active(进行中), finished(已结束)

class Meeting(MeetingBase, table=True):
    id: Optional[int] = Field(default=None, primary_key=True)
    created_at: datetime = Field(default_factory=datetime.now) # 创建时间
    
    # 关联属性
    attendees: List[User] = Relationship(back_populates="meetings", link_model=MeetingAttendeeLink) # 参会人员列表
    attachments: List[Attachment] = Relationship(back_populates="meeting") # 附件列表

class MeetingRead(MeetingBase):
    id: int
    created_at: datetime

# 备忘录/后续事项模型
class NoteBase(SQLModel):
    title: str = Field(index=True)
    content: str = Field(default="")
    status: str = Field(default="pending") # pending, done

class Note(NoteBase, table=True):
    id: Optional[int] = Field(default=None, primary_key=True)
    created_at: datetime = Field(default_factory=datetime.now)
    updated_at: datetime = Field(default_factory=datetime.now)

# 系统设置模型 (Key-Value)
class SystemSetting(SQLModel, table=True):
    key: str = Field(primary_key=True)
    value: str # JSON encoded value or simple string
    description: Optional[str] = None

# 系统的同屏/跟随状态模型
class MeetingSyncState(SQLModel, table=True):
    meeting_id: int = Field(primary_key=True)
    file_id: int 
    page_number: int
    file_url: Optional[str] = None # Optional, mainly for client convenience
    timestamp: float # Unix timestamp to prevent old commands
    is_syncing: bool = Field(default=False) # Master switch for this meeting


class NoteRead(NoteBase):
    id: int
    created_at: datetime
    updated_at: datetime

class NoteUpdate(SQLModel):
    title: Optional[str] = None
    content: Optional[str] = None
    status: Optional[str] = None

# 设备管理模型
class DeviceBase(SQLModel):
    device_id: str = Field(unique=True, index=True) # 硬件标识
    name: Optional[str] = None # 设备名称 (e.g. "张三的平板")
    alias: Optional[str] = None # 用户自定义别名
    model: Optional[str] = None # 型号
    mac_address: Optional[str] = None
    os_version: Optional[str] = None
    app_version: Optional[str] = None
    ip_address: Optional[str] = None
    battery_level: Optional[int] = None # 0-100
    is_charging: bool = Field(default=False)
    storage_total: Optional[int] = None # bytes
    storage_available: Optional[int] = None # bytes
    last_active_at: datetime = Field(default_factory=datetime.now)
    status: str = Field(default="active") # active, blocked

class Device(DeviceBase, table=True):
    id: Optional[int] = Field(default=None, primary_key=True)

class DeviceRead(DeviceBase):
    id: int

# APP更新模型
class AppUpdateBase(SQLModel):
    version_code: int # 安卓版本号 (用于比较)
    version_name: str # 显示版本 (e.g. "1.0.2")
    release_notes: Optional[str] = None
    download_url: str # APK下载地址
    is_force_update: bool = Field(default=False)
    created_at: datetime = Field(default_factory=datetime.now)

class AppUpdate(AppUpdateBase, table=True):
    id: Optional[int] = Field(default=None, primary_key=True)

class AppUpdateRead(AppUpdateBase):
    id: int

# 设备指令模型 (用于批量更新/重启等远程操作)
class DeviceCommandBase(SQLModel):
    device_id: str = Field(index=True)  # 目标设备的device_id
    command_type: str  # "update_app", "restart", "lock"
    payload: Optional[str] = None  # JSON格式附加参数
    status: str = Field(default="pending")  # pending, acked, failed, expired

class DeviceCommand(DeviceCommandBase, table=True):
    id: Optional[int] = Field(default=None, primary_key=True)
    created_at: datetime = Field(default_factory=datetime.now)
    acked_at: Optional[datetime] = None

class DeviceCommandRead(DeviceCommandBase):
    id: int
    created_at: datetime
    acked_at: Optional[datetime]


# ==================== 投票功能模型 ====================

class Vote(SQLModel, table=True):
    """投票主表"""
    id: Optional[int] = Field(default=None, primary_key=True)
    meeting_id: int = Field(foreign_key="meeting.id")
    title: str
    description: Optional[str] = None
    is_multiple: bool = Field(default=False)  # 是否多选
    is_anonymous: bool = Field(default=False)  # 是否匿名
    max_selections: int = Field(default=1)  # 最多可选数量
    duration_seconds: int = Field(default=60)  # 投票时长（秒）
    status: str = Field(default="draft")  # draft/active/closed
    started_at: Optional[datetime] = None
    created_at: datetime = Field(default_factory=datetime.now)

class VoteOption(SQLModel, table=True):
    """投票选项"""
    id: Optional[int] = Field(default=None, primary_key=True)
    vote_id: int = Field(foreign_key="vote.id")
    content: str
    sort_order: int = Field(default=0)

class UserVote(SQLModel, table=True):
    """用户投票记录"""
    id: Optional[int] = Field(default=None, primary_key=True)
    vote_id: int = Field(foreign_key="vote.id")
    user_id: int = Field(foreign_key="user.id")
    option_id: int = Field(foreign_key="voteoption.id")
    voted_at: datetime = Field(default_factory=datetime.now)

class VoteRead(SQLModel):
    id: int
    meeting_id: int
    title: str
    description: Optional[str]
    is_multiple: bool
    is_anonymous: bool
    max_selections: int
    duration_seconds: int
    status: str
    started_at: Optional[datetime]
    created_at: datetime
    options: List["VoteOptionRead"] = []
    remaining_seconds: Optional[int] = None  # 计算剩余时间
    wait_seconds: Optional[int] = None # 距离开始因该等待的秒数 (倒计时)

class VoteOptionRead(SQLModel):
    id: int
    content: str
    sort_order: int
    vote_count: Optional[int] = None  # 投票结果时使用
    percent: Optional[float] = None
    voters: List[str] = []  # 投票人姓名列表 (非匿名且已结束/查看结果时返回)

class VoteCreate(SQLModel):
    meeting_id: int
    title: str
    description: Optional[str] = None
    is_multiple: bool = False
    is_anonymous: bool = False
    max_selections: int = 1
    duration_seconds: int = 60
    options: List[str]  # 选项内容列表

class VoteOptionResult(SQLModel):
    option_id: int
    content: str
    count: int
    percent: float
    voters: List[str] = []

class VoteResult(SQLModel):
    vote_id: int
    title: str
    total_voters: int
    results: List[VoteOptionResult]

class VoteOptionContent(SQLModel):
    content: str

class VoteStatusUpdate(SQLModel):
    status: str

class VoteSubmit(SQLModel):
    user_id: int
    option_ids: List[int]


# ========== 抽签模型 ==========

class Lottery(SQLModel, table=True):
    """抽签轮次记录"""
    id: Optional[int] = Field(default=None, primary_key=True)
    meeting_id: int = Field(foreign_key="meeting.id")
    title: str 
    count: int = Field(default=1)  # 本轮中奖人数
    allow_repeat: bool = Field(default=False)  # 是否允许重复中奖
    status: str = Field(default="pending")  # pending/active/finished
    created_at: datetime = Field(default_factory=datetime.now)
    
    # 关联中奖者
    winners: List["LotteryWinner"] = Relationship(back_populates="lottery")


class LotteryWinner(SQLModel, table=True):
    """抽签中奖记录"""
    id: Optional[int] = Field(default=None, primary_key=True)
    lottery_id: int = Field(foreign_key="lottery.id")
    user_id: Optional[int] = Field(default=None, foreign_key="user.id")  # 可选，允许临时用户
    user_name: str  # 中奖者姓名
    winning_at: datetime = Field(default_factory=datetime.now)
    
    lottery: Optional[Lottery] = Relationship(back_populates="winners")
    user: Optional[User] = Relationship()


class LotteryParticipant(SQLModel, table=True):
    """抽签参与者池 (解决刷新数据丢失问题)"""
    meeting_id: int = Field(foreign_key="meeting.id", primary_key=True)
    user_id: int = Field(primary_key=True)
    user_name: str # 冗余存个名字，方便显示
    avatar: Optional[str] = None 
    department: Optional[str] = None
    status: str = Field(default="joined") # joined: 已加入, left: 已退出
    is_winner: bool = Field(default=False) # 是否已中奖 (防止重复中奖)
    created_at: datetime = Field(default_factory=datetime.now)
