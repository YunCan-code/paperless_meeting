from sqlmodel import Session, select, func, delete
from database import engine, create_db_and_tables
from models import Meeting, MeetingType, User
import random
import json
from datetime import datetime, timedelta

def populate():
    # Ensure tables exist
    create_db_and_tables()

    with Session(engine) as session:
        print("Starting data population...")

        # 1. Meeting Types
        types = ["党委会", "办公会", "部门例会", "战略研讨会", "临时紧急会议", 
                 "全员大会", "技术评审会", "安全生产会", "供应链协调会", "财务分析会"]
        db_types = []
        for t_name in types:
            mt = session.exec(select(MeetingType).where(MeetingType.name == t_name)).first()
            if not mt:
                mt = MeetingType(name=t_name, description=f"随机生成的{t_name}")
                session.add(mt)
                session.commit()
                session.refresh(mt)
            db_types.append(mt)
        
        # 2. Users
        last_names = "赵钱孙李周吴郑王冯陈"
        first_names = "伟芳娜敏静秀英丽强磊军洋勇平刚杰"
        depts = ["研发部", "市场部", "行政部", "销售部", "运维部", "财务部"]
        roles = ["主讲人", "参会人员"]
        districts = ['市辖区', '高新区', '呈贡区', '盘龙区', '官渡区', '西山区', '五华区']
        
        db_users = []
        # Create or Get 50 users
        for i in range(50):
            # Deterministic name generation for stability or just random
            name = random.choice(last_names) + random.choice(first_names) + (random.choice(first_names) if random.random() > 0.5 else "")
            
            u = session.exec(select(User).where(User.name == name)).first()
            if not u:
                u = User(
                    name=name,
                    district=random.choice(districts),
                    department=random.choice(depts),
                    role=random.choice(roles),
                    phone=f"138{random.randint(1000,9999)}{random.randint(1000,9999)}",
                    remark="自动生成",
                    is_active=True,
                    last_login=datetime.now() - timedelta(days=random.randint(0, 30))
                )
                session.add(u)
                session.commit()
                session.refresh(u)
            db_users.append(u)

        # 3. Meetings
        # Clear existing meetings to ensure we regenerate fresh demo data
        # Note: Delete strictly implies we want fresh meeting data.
        print("Clearing old meetings...")
        session.exec(delete(Meeting))
        session.commit()

        print("Generating new meetings...")
        locations = ["第一会议室", "第二会议室", "大会议厅", "远程视频", "VIP接待室"]
        
        # Create about 30-50 meetings
        total_created = 0
        for mt in db_types:
            for _ in range(random.randint(3, 6)):
                # Agenda
                agenda_items = [
                    {"time": "09:00", "content": "签到入场"},
                    {"time": "09:30", "content": "领导致辞"},
                    {"time": "10:00", "content": "主题汇报"},
                    {"time": "11:30", "content": "自由讨论"}
                ]
                
                # Time: some past, some future
                days_offset = random.randint(-15, 30)
                meeting_time = datetime.now() + timedelta(days=days_offset, hours=random.randint(9, 16))
                
                # Status logic
                status = "finished" if meeting_time < datetime.now() else "scheduled"
                
                # Speaker
                potential_speakers = [u for u in db_users if u.role == "主讲人"]
                speaker_name = random.choice(potential_speakers).name if potential_speakers else "未知主讲人"

                meeting = Meeting(
                    title=f"{mt.name} - 第{random.randint(1,20)}期",
                    description="本次会议旨在讨论...",
                    start_time=meeting_time,
                    location=random.choice(locations),
                    speaker=speaker_name,
                    agenda=json.dumps(agenda_items, ensure_ascii=False),
                    meeting_type_id=mt.id,
                    is_active=True,
                    status=status
                )
                
                # Attendees (simple list assignment if model supports it, 
                # but for simplicity we rely on SQLModel relationship or skip explicit link if complex)
                # meeting.attendees = random.sample(db_users, k=5) 
                
                session.add(meeting)
                total_created += 1
        
        session.commit()
        print(f"Done! {total_created} meetings generated.")

if __name__ == "__main__":
    populate()
