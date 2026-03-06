"""
种子数据脚本 — 生成三月份的模拟会议、人员、议程等数据
用法:
  本地:  cd backend && python seed_data.py
  Docker: docker compose exec backend python seed_data.py

  强制重置（清空旧数据）:
  本地:  cd backend && python seed_data.py --force
  Docker: docker compose exec backend python seed_data.py --force
"""

import json
import random
import sys
from datetime import datetime
from sqlmodel import Session, select, delete
from database import engine
from models import User, MeetingType, Meeting, MeetingAttendeeLink

# ============================================================
# 1. 人员数据
# ============================================================

USERS = [
    {"name": "张伟", "department": "党委办公室", "position": "主任", "phone": "13800000001", "district": "市直机关"},
    {"name": "李芳", "department": "党委办公室", "position": "副主任", "phone": "13800000002", "district": "市直机关"},
    {"name": "王强", "department": "组织部", "position": "部长", "phone": "13800000003", "district": "市直机关"},
    {"name": "赵敏", "department": "宣传部", "position": "部长", "phone": "13800000004", "district": "市直机关"},
    {"name": "刘洋", "department": "纪检监察室", "position": "主任", "phone": "13800000005", "district": "市直机关"},
    {"name": "陈静", "department": "人力资源部", "position": "经理", "phone": "13800000006", "district": "市直机关"},
    {"name": "杨帆", "department": "财务部", "position": "总监", "phone": "13800000007", "district": "市直机关"},
    {"name": "黄磊", "department": "信息中心", "position": "主任", "phone": "13800000008", "district": "市直机关"},
    {"name": "周婷", "department": "法规处", "position": "处长", "phone": "13800000009", "district": "市直机关"},
    {"name": "吴刚", "department": "综合管理部", "position": "副部长", "phone": "13800000010", "district": "市直机关"},
    {"name": "孙丽", "department": "后勤保障部", "position": "科长", "phone": "13800000011", "district": "开发区"},
    {"name": "马超", "department": "项目管理办", "position": "主任", "phone": "13800000012", "district": "开发区"},
    {"name": "朱红", "department": "审计部", "position": "审计员", "phone": "13800000013", "district": "市直机关"},
    {"name": "胡明", "department": "安全生产部", "position": "副经理", "phone": "13800000014", "district": "高新区"},
    {"name": "林小燕", "department": "档案管理中心", "position": "馆员", "phone": "13800000015", "district": "市直机关"},
    {"name": "郭志远", "department": "信息中心", "position": "工程师", "phone": "13800000016", "district": "市直机关"},
    {"name": "何晓东", "department": "综合管理部", "position": "科员", "phone": "13800000017", "district": "市直机关"},
    {"name": "罗小敏", "department": "宣传部", "position": "干事", "phone": "13800000018", "district": "市直机关"},
    {"name": "徐浩", "department": "组织部", "position": "副部长", "phone": "13800000019", "district": "市直机关"},
    {"name": "高雅", "department": "党委办公室", "position": "秘书", "phone": "13800000020", "district": "市直机关"},
    {"name": "谢飞", "department": "项目管理办", "position": "项目经理", "phone": "13800000021", "district": "开发区"},
    {"name": "宋佳", "department": "人力资源部", "position": "专员", "phone": "13800000022", "district": "市直机关"},
    {"name": "唐亮", "department": "财务部", "position": "会计", "phone": "13800000023", "district": "市直机关"},
    {"name": "韩雪", "department": "法规处", "position": "法律顾问", "phone": "13800000024", "district": "市直机关"},
    {"name": "冯强", "department": "安全生产部", "position": "主任", "phone": "13800000025", "district": "高新区"},
]

# ============================================================
# 2. 会议类型
# ============================================================

MEETING_TYPES = [
    {"name": "党委会", "description": "党委重要决策会议"},
    {"name": "办公会", "description": "日常行政工作会议"},
    {"name": "专题研讨", "description": "专项议题深入讨论"},
    {"name": "培训学习", "description": "业务培训与学习交流"},
    {"name": "述职评议", "description": "干部述职与民主评议"},
]

# ============================================================
# 3. 会议数据模板
# ============================================================

LOCATIONS = [
    "一楼多功能厅", "三楼会议室A", "三楼会议室B", "五楼视频会议室",
    "行政楼201", "行政楼305", "党员活动中心", "二楼报告厅",
]

MEETINGS_TEMPLATE = [
    # ---- 3月3日 周一 ----
    {
        "title": "2026年第一季度工作总结暨第二季度部署会",
        "type": "办公会",
        "date": (3, 3), "start": "09:00", "end": "11:30",
        "location": "一楼多功能厅",
        "speaker": "张伟",
        "status": "finished",
        "agenda": [
            {"time": "09:00", "content": "一季度各部门工作汇报"},
            {"time": "09:45", "content": "财务部门经费使用情况通报"},
            {"time": "10:15", "content": "茶歇"},
            {"time": "10:30", "content": "二季度重点工作安排"},
            {"time": "11:00", "content": "讨论与答疑"},
        ],
        "attendee_count": 20,
        "roles": {"张伟": "主讲人", "李芳": "主讲人", "杨帆": "特邀嘉宾"},
    },
    # ---- 3月5日 周三 ----
    {
        "title": "党委理论学习中心组第三次集体学习",
        "type": "党委会",
        "date": (3, 5), "start": "14:30", "end": "17:00",
        "location": "党员活动中心",
        "speaker": "王强",
        "status": "finished",
        "agenda": [
            {"time": "14:30", "content": "集体学习《关于加强新时代廉洁文化建设的意见》"},
            {"time": "15:30", "content": "交流研讨发言"},
            {"time": "16:30", "content": "总结讲话"},
        ],
        "attendee_count": 12,
        "roles": {"王强": "主讲人", "张伟": "特邀嘉宾"},
    },
    # ---- 3月7日 周五 ----
    {
        "title": "数字化转型专题研讨会",
        "type": "专题研讨",
        "date": (3, 7), "start": "09:30", "end": "12:00",
        "location": "五楼视频会议室",
        "speaker": "黄磊",
        "status": "finished",
        "agenda": [
            {"time": "09:30", "content": "数字政务平台建设进展汇报"},
            {"time": "10:00", "content": "无纸化办公系统使用培训"},
            {"time": "10:45", "content": "各部门信息化需求收集"},
            {"time": "11:30", "content": "下一步技术路线讨论"},
        ],
        "attendee_count": 15,
        "roles": {"黄磊": "主讲人", "郭志远": "特邀嘉宾"},
    },
    # ---- 3月10日 周一 ----
    {
        "title": "安全生产工作会议",
        "type": "办公会",
        "date": (3, 10), "start": "10:00", "end": "11:30",
        "location": "三楼会议室A",
        "speaker": "冯强",
        "status": "finished",
        "agenda": [
            {"time": "10:00", "content": "近期安全隐患排查情况通报"},
            {"time": "10:30", "content": "消防安全演练方案讨论"},
            {"time": "11:00", "content": "应急预案更新说明"},
        ],
        "attendee_count": 10,
        "roles": {"冯强": "主讲人", "胡明": "特邀嘉宾"},
    },
    # ---- 3月12日 周三 ----
    {
        "title": "2025年度干部述职评议大会",
        "type": "述职评议",
        "date": (3, 12), "start": "09:00", "end": "17:00",
        "location": "二楼报告厅",
        "speaker": "张伟",
        "status": "finished",
        "agenda": [
            {"time": "09:00", "content": "开场致辞及评议规则说明"},
            {"time": "09:20", "content": "部门负责人述职（上午场）"},
            {"time": "12:00", "content": "午休"},
            {"time": "14:00", "content": "部门负责人述职（下午场）"},
            {"time": "16:00", "content": "民主测评投票"},
            {"time": "16:30", "content": "总结讲话"},
        ],
        "attendee_count": 25,
        "roles": {"张伟": "主讲人", "王强": "特邀嘉宾", "刘洋": "特邀嘉宾"},
    },
    # ---- 3月14日 周五 ----
    {
        "title": "新入职员工岗前培训",
        "type": "培训学习",
        "date": (3, 14), "start": "09:00", "end": "16:30",
        "location": "三楼会议室B",
        "speaker": "陈静",
        "status": "finished",
        "agenda": [
            {"time": "09:00", "content": "单位概况及组织架构介绍"},
            {"time": "10:00", "content": "规章制度与工作纪律"},
            {"time": "11:00", "content": "信息化系统操作培训"},
            {"time": "14:00", "content": "公文写作与办公礼仪"},
            {"time": "15:30", "content": "新老员工座谈交流"},
        ],
        "attendee_count": 8,
        "roles": {"陈静": "主讲人", "黄磊": "特邀嘉宾", "宋佳": "特邀嘉宾"},
    },
    # ---- 3月17日 周一 ----
    {
        "title": "财务预算审核专题会",
        "type": "专题研讨",
        "date": (3, 17), "start": "14:00", "end": "16:00",
        "location": "行政楼201",
        "speaker": "杨帆",
        "status": "finished",
        "agenda": [
            {"time": "14:00", "content": "2026年度预算执行情况分析"},
            {"time": "14:40", "content": "各部门追加预算申请审议"},
            {"time": "15:20", "content": "经费管控措施讨论"},
        ],
        "attendee_count": 8,
        "roles": {"杨帆": "主讲人", "唐亮": "特邀嘉宾"},
    },
    # ---- 3月19日 周三 ----
    {
        "title": "法治建设与合规管理工作推进会",
        "type": "办公会",
        "date": (3, 19), "start": "10:00", "end": "11:30",
        "location": "行政楼305",
        "speaker": "周婷",
        "status": "finished",
        "agenda": [
            {"time": "10:00", "content": "法治建设年度工作计划通报"},
            {"time": "10:30", "content": "合同管理及风险防控培训"},
            {"time": "11:00", "content": "典型案例分析"},
        ],
        "attendee_count": 12,
        "roles": {"周婷": "主讲人", "韩雪": "特邀嘉宾"},
    },
    # ---- 3月21日 周五 ----
    {
        "title": "3月份党委扩大会议",
        "type": "党委会",
        "date": (3, 21), "start": "09:00", "end": "12:00",
        "location": "一楼多功能厅",
        "speaker": "张伟",
        "status": "finished",
        "agenda": [
            {"time": "09:00", "content": "审议通过《关于加强作风建设的实施方案》"},
            {"time": "09:45", "content": "人事任免议题讨论"},
            {"time": "10:30", "content": "茶歇"},
            {"time": "10:45", "content": "近期重点项目进展汇报"},
            {"time": "11:30", "content": "党委书记总结部署"},
        ],
        "attendee_count": 18,
        "roles": {"张伟": "主讲人", "王强": "主讲人", "刘洋": "特邀嘉宾"},
    },
    # ---- 3月24日 周一 ----
    {
        "title": "乡村振兴项目对接座谈会",
        "type": "专题研讨",
        "date": (3, 24), "start": "14:30", "end": "17:00",
        "location": "三楼会议室A",
        "speaker": "马超",
        "status": "finished",
        "agenda": [
            {"time": "14:30", "content": "帮扶村现状及需求介绍"},
            {"time": "15:00", "content": "各帮扶责任人汇报工作进展"},
            {"time": "16:00", "content": "产业帮扶项目方案讨论"},
            {"time": "16:30", "content": "下一步工作安排"},
        ],
        "attendee_count": 14,
        "roles": {"马超": "主讲人", "谢飞": "特邀嘉宾"},
    },
    # ---- 3月26日 周三 ----
    {
        "title": "办公设备更新及采购评审会",
        "type": "办公会",
        "date": (3, 26), "start": "10:00", "end": "11:30",
        "location": "行政楼201",
        "speaker": "孙丽",
        "status": "finished",
        "agenda": [
            {"time": "10:00", "content": "现有设备使用状况统计"},
            {"time": "10:20", "content": "各供应商方案比选"},
            {"time": "11:00", "content": "采购预算及流程审批"},
        ],
        "attendee_count": 7,
        "roles": {"孙丽": "主讲人", "杨帆": "特邀嘉宾"},
    },
    # ---- 3月28日 周五 ----
    {
        "title": "保密工作专题培训",
        "type": "培训学习",
        "date": (3, 28), "start": "14:00", "end": "16:00",
        "location": "二楼报告厅",
        "speaker": "刘洋",
        "status": "finished",
        "agenda": [
            {"time": "14:00", "content": "保密法规解读"},
            {"time": "14:45", "content": "涉密信息处理规范"},
            {"time": "15:30", "content": "典型泄密案例警示教育"},
        ],
        "attendee_count": 22,
        "roles": {"刘洋": "主讲人", "林小燕": "特邀嘉宾"},
    },
    # ---- 3月31日 周一 ----
    {
        "title": "第一季度综合考核评审会",
        "type": "述职评议",
        "date": (3, 31), "start": "09:00", "end": "12:00",
        "location": "一楼多功能厅",
        "speaker": "王强",
        "status": "scheduled",
        "agenda": [
            {"time": "09:00", "content": "考核办法及评分标准说明"},
            {"time": "09:30", "content": "各部门一季度工作实绩汇报"},
            {"time": "11:00", "content": "考评组合议打分"},
            {"time": "11:30", "content": "反馈与整改要求"},
        ],
        "attendee_count": 20,
        "roles": {"王强": "主讲人", "徐浩": "特邀嘉宾", "张伟": "特邀嘉宾"},
    },

    # ---- 额外：同一天多会议 ----
    {
        "title": "宣传工作周例会",
        "type": "办公会",
        "date": (3, 5), "start": "09:00", "end": "10:00",
        "location": "三楼会议室B",
        "speaker": "赵敏",
        "status": "finished",
        "agenda": [
            {"time": "09:00", "content": "上周宣传任务完成情况"},
            {"time": "09:20", "content": "本周宣传重点安排"},
            {"time": "09:40", "content": "新媒体账号运营数据分析"},
        ],
        "attendee_count": 6,
        "roles": {"赵敏": "主讲人", "罗小敏": "特邀嘉宾"},
    },
    {
        "title": "档案数字化项目验收会",
        "type": "专题研讨",
        "date": (3, 10), "start": "14:00", "end": "16:00",
        "location": "五楼视频会议室",
        "speaker": "林小燕",
        "status": "finished",
        "agenda": [
            {"time": "14:00", "content": "项目完成情况汇报"},
            {"time": "14:30", "content": "系统功能演示及验收"},
            {"time": "15:30", "content": "验收意见讨论"},
        ],
        "attendee_count": 9,
        "roles": {"林小燕": "主讲人", "黄磊": "特邀嘉宾", "郭志远": "特邀嘉宾"},
    },
    # 无议程的会议
    {
        "title": "临时碰头会 — 近期舆情应对",
        "type": "办公会",
        "date": (3, 13), "start": "16:00", "end": "17:00",
        "location": "行政楼305",
        "speaker": "赵敏",
        "status": "finished",
        "agenda": None,
        "description": "就近期网络舆情事件进行紧急商讨，制定应对方案。",
        "attendee_count": 5,
        "roles": {"赵敏": "主讲人"},
    },
    # 只有描述没有结构化议程的会议
    {
        "title": "智慧园区建设方案汇报",
        "type": "专题研讨",
        "date": (3, 20), "start": "15:00", "end": "17:00",
        "location": "五楼视频会议室",
        "speaker": "黄磊",
        "status": "finished",
        "agenda": None,
        "description": "由信息中心汇报智慧园区建设总体方案，包括物联网平台、视频监控升级、能耗管理系统等内容。参会领导就方案的技术路线、投资预算、实施节奏进行讨论。",
        "attendee_count": 11,
        "roles": {"黄磊": "主讲人", "马超": "特邀嘉宾", "郭志远": "特邀嘉宾"},
    },
]


def seed(force: bool = False):
    with Session(engine) as session:
        # 检查是否已有数据
        existing = session.exec(select(User)).first()
        if existing:
            if not force:
                print("⚠️  数据库中已有用户数据，跳过种子填充。")
                print("    如需重新生成，请使用 --force 参数：")
                print("    docker compose exec backend python seed_data.py --force")
                return
            else:
                print("🗑️  --force 模式：清空旧数据...")
                session.exec(delete(MeetingAttendeeLink))
                session.exec(delete(Meeting))
                session.exec(delete(MeetingType))
                session.exec(delete(User))
                session.commit()
                print("    ✅ 旧数据已清空")

        print("🌱 开始填充种子数据...")

        # --- 创建用户 ---
        user_map: dict[str, User] = {}
        for u in USERS:
            user = User(
                name=u["name"],
                department=u.get("department"),
                position=u.get("position"),
                phone=u.get("phone"),
                district=u.get("district"),
                password="123456",
                is_active=True,
            )
            session.add(user)
            session.flush()
            user_map[u["name"]] = user
        print(f"  ✅ 创建 {len(user_map)} 个用户")

        # --- 创建会议类型 ---
        type_map: dict[str, MeetingType] = {}
        for mt in MEETING_TYPES:
            meeting_type = MeetingType(name=mt["name"], description=mt["description"])
            session.add(meeting_type)
            session.flush()
            type_map[mt["name"]] = meeting_type
        print(f"  ✅ 创建 {len(type_map)} 个会议类型")

        # --- 创建会议 ---
        all_user_names = list(user_map.keys())
        meeting_count = 0

        for m in MEETINGS_TEMPLATE:
            month, day = m["date"]
            sh, sm = map(int, m["start"].split(":"))
            eh, em = map(int, m["end"].split(":"))
            start_time = datetime(2026, month, day, sh, sm)
            end_time = datetime(2026, month, day, eh, em)

            agenda_json = None
            if m.get("agenda"):
                agenda_json = json.dumps(m["agenda"], ensure_ascii=False)

            meeting = Meeting(
                title=m["title"],
                meeting_type_id=type_map[m["type"]].id,
                start_time=start_time,
                end_time=end_time,
                location=m["location"],
                speaker=m["speaker"],
                agenda=agenda_json,
                description=m.get("description"),
                status=m["status"],
            )
            session.add(meeting)
            session.flush()

            # 分配与会人员
            roles_map = m.get("roles", {})
            named_users = set(roles_map.keys())

            # 先添加有明确角色的人
            for name, role in roles_map.items():
                if name in user_map:
                    link = MeetingAttendeeLink(
                        meeting_id=meeting.id,
                        user_id=user_map[name].id,
                        meeting_role=role,
                    )
                    session.add(link)

            # 随机补齐剩余参会人员
            remaining_count = m["attendee_count"] - len(named_users)
            available = [n for n in all_user_names if n not in named_users]
            random.shuffle(available)
            for name in available[:remaining_count]:
                link = MeetingAttendeeLink(
                    meeting_id=meeting.id,
                    user_id=user_map[name].id,
                    meeting_role="参会人员",
                )
                session.add(link)

            meeting_count += 1

        session.commit()
        print(f"  ✅ 创建 {meeting_count} 场会议（含与会人员分配）")
        print("🎉 种子数据填充完成！")


if __name__ == "__main__":
    force = "--force" in sys.argv
    seed(force=force)
