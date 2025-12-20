from sqlmodel import Session, select
from database import engine, create_db_and_tables
from models import Meeting, MeetingType, User, MeetingAttendeeLink
import random
from datetime import datetime, timedelta

# Create tables if new fields added
create_db_and_tables()

def populate():
    with Session(engine) as session:
        print("Cleaning up existing data...")
        # Optional: Clear tables to avoid duplication or conflicts with new schema
        # session.exec("DELETE FROM user") # SQLModel doesn't support raw SQL easily this way in all versions, skipping clear for safety or assume fresh start
        
        # 1. Create Meeting Types
        types = ["党委会", "办公会", "部门例会", "战略研讨会", "临时紧急会议", "全员大会", "技术评审会", "安全生产会", "供应链协调会", "财务分析会"]
        type_objs = []
        print("Creating Meeting Types...")
        for t_name in types:
            existing = session.exec(select(MeetingType).where(MeetingType.name == t_name)).first()
            if not existing:
                mt = MeetingType(name=t_name, description=f"随机生成的{t_name}")
                session.add(mt)
                session.commit()
                session.refresh(mt)
                type_objs.append(mt)
            else:
                type_objs.append(existing)
        
        # 2. Create Users (NEW)
        print("Creating Users...")
        # Real-ish data
        last_names = "赵钱孙李周吴郑王冯陈"
        first_names = "伟芳娜敏静秀英丽强磊军洋勇平刚杰"
        depts = ["研发部", "市场部", "行政部", "销售部", "运维部", "财务部"]
        roles = ["主讲人", "参会人员"]
        districts = ['市辖区', '高新区', '呈贡区', '盘龙区', '官渡区', '西山区', '五华区']
        
        user_objs = []
        # Create 25 users
        for i in range(25):
            name = random.choice(last_names) + random.choice(first_names) + (random.choice(first_names) if random.random()>0.5 else "")
            
            # Check exist (by phone to be realistic, or just skip)
            # existing_user = session.exec(select(User).where(User.username == username)).first()
            
            u = User(
                name=name,
                # username removed
                email=f"user{i+100}@soe-meeting.com",
                phone=f"138{random.randint(10000000, 99999999)}",
                district=random.choice(districts),
                department=random.choice(depts),
                position="员工",
                role=random.choice(roles),
                is_active=True,
                password="password123", # Default pwd
                last_login=datetime.now() - timedelta(days=random.randint(0, 30))
            )
            session.add(u)
            user_objs.append(u)
        session.commit()
        for u in user_objs: session.refresh(u)

        # 3. Generate Meetings
        print("Generating Meetings...")
        start_date = datetime(2025, 12, 1)
        locations = ["第一会议室", "第二会议室", "大会议厅", "远程视频", "VIP接待室"]
        
        total_meetings = 0
        for i in range(31):
            current_date = start_date + timedelta(days=i)
            count = random.randint(0, 5) # Few meetings per day
            
            for _ in range(count):
                hour = random.randint(8, 18)
                mt = random.choice(type_objs)
                topic_suffix = random.choice(["进度汇报", "问题讨论", "年终总结", "二期规划"])
                meeting_time = current_date.replace(hour=hour, minute=0)
                
                status = "finished" if meeting_time < datetime.now() else "scheduled"
                
                meeting = Meeting(
                    title=f"{mt.name}-{topic_suffix}",
                    meeting_type_id=mt.id,
                    start_time=meeting_time,
                    location=random.choice(locations),
                    status=status
                )
                
                # Random attendees
                attendees = random.sample(user_objs, k=random.randint(3, 10))
                meeting.attendees = attendees
                
                session.add(meeting)
                total_meetings += 1
            
        session.commit()
        print(f"Done! {len(user_objs)} Users, {total_meetings} Meetings created.")

if __name__ == "__main__":
    populate()
