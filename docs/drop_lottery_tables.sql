-- 删除抽签相关数据表
-- 在 VPS 上执行此脚本: psql -U paperless -d paperless_meeting -f drop_lottery_tables.sql

-- 先删除有外键依赖的子表
DROP TABLE IF EXISTS lotterywinner CASCADE;

-- 再删除主表
DROP TABLE IF EXISTS lottery CASCADE;

-- 验证删除成功
SELECT table_name FROM information_schema.tables WHERE table_name IN ('lottery', 'lotterywinner');
