from sqlmodel import Session, select
from database import engine
from models import Meeting, MeetingType
import random
from datetime import datetime, timedelta

def populate():
    with Session(engine) as session:
        # 1. Create Meeting Types
        types = ["党委会", "办公会", "部门例会", "战略研讨会", "临时紧急会议", "全员大会", "技术评审会", "安全生产会", "供应链协调会", "财务分析会"]
        type_objs = []
        print("Creating Meeting Types...")
        for t_name in types:
            # Check if exists to avoid error if unique constraint
            existing = session.exec(select(MeetingType).where(MeetingType.name == t_name)).first()
            if not existing:
                mt = MeetingType(name=t_name, description=f"随机生成的{t_name}")
                session.add(mt)
                session.commit()
                session.refresh(mt)
                type_objs.append(mt)
            else:
                type_objs.append(existing)
        
        # 2. Generate Meetings for December 2025
        # Assuming current context year is 2025
        start_date = datetime(2025, 12, 1)
        locations = ["第一会议室", "第二会议室", "大会议厅", "远程视频", "VIP接待室", "305会议室", "C座研讨间"]
        
        total_meetings = 0
        print("Generating Meetings...")
        for i in range(31):
            current_date = start_date + timedelta(days=i)
            # Random count 0-10 per day
            count = random.randint(0, 10)
            
            for _ in range(count):
                # Random time between 8:00 and 18:00
                hour = random.randint(8, 18)
                minute = random.choice([0, 15, 30, 45])
                meeting_time = current_date.replace(hour=hour, minute=minute)
                
                mt = random.choice(type_objs)
                topic_suffix = random.choice(["进度汇报", "问题讨论", "年终总结", "二期规划", "风险评估", "专项整改", "启动仪式", "复盘分析"])
                
                # Determine status based on current time (Mocking 'now' as needed or using real now)
                # Since user time is 2025-12-18, meetings before this should be finished
                now = datetime.now()
                if meeting_time < now:
                    status = "finished"
                else:
                    status = "scheduled"
                
                meeting = Meeting(
                    title=f"{mt.name}-{topic_suffix}",
                    meeting_type_id=mt.id,
                    start_time=meeting_time,
                    location=random.choice(locations),
                    status=status
                )
                session.add(meeting)
                total_meetings += 1
            
        session.commit()
        print(f"Successfully generated {total_meetings} meetings for December 2025.")

if __name__ == "__main__":
    populate()
