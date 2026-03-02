import os
from sqlmodel import create_engine, Session, select
from models import User

DATABASE_URL = "postgresql://postgres:123456@localhost:5432/paperless_meeting"
engine = create_engine(DATABASE_URL)

def fix_db_roles():
    with Session(engine) as session:
        users = session.exec(select(User)).all()
        updated_count = 0
        for user in users:
            if user.role == "主讲人":
                user.role = "管理员"
                updated_count += 1
            elif user.role == "参会人员":
                user.role = "普通会员"
                updated_count += 1
            elif user.role not in ["管理员", "普通会员"]:
                user.role = "普通会员"
                updated_count += 1
        session.commit()
        print(f"SQL execution complete: Migrated {updated_count} user roles to the new system roles format.")

if __name__ == "__main__":
    fix_db_roles()
