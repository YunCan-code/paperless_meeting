-- 此处存放本次会议人员重构带来的 2 个表结构变化：
-- 1. meeting 增加 end_time
-- 2. meetingattendeelink 增加 meeting_role

-- 第一步：给 meeting 表加 end_time 字段 (如果之前没有加过)
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_name = 'meeting' AND column_name = 'end_time'
    ) THEN
        ALTER TABLE meeting ADD COLUMN end_time TIMESTAMP WITHOUT TIME ZONE;
    END IF;
END $$;

-- 第二步：给 meetingattendeelink 表加 meeting_role 字段
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_name = 'meetingattendeelink' AND column_name = 'meeting_role'
    ) THEN
        ALTER TABLE meetingattendeelink ADD COLUMN meeting_role VARCHAR;
        -- 设置默认角色为参会人员
        UPDATE meetingattendeelink SET meeting_role = '参会人员' WHERE meeting_role IS NULL;
    END IF;
END $$;

-- 第三步：处理 user 表的 role 字段 (因为代码中已移除，数据库中需取消非空约束或删除)
DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_name = 'user' AND column_name = 'role'
    ) THEN
        -- 取消非空约束，防止插入报错
        ALTER TABLE "user" ALTER COLUMN role DROP NOT NULL;
        -- 如果确定不再需要，也可以选择直接删除该列 (可选)
        -- ALTER TABLE "user" DROP COLUMN role;
    END IF;
END $$;

