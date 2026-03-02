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
