-- ============================================================
-- Orion Key 初始化数据
-- 首次部署时手动执行一次: psql -U <user> -d <db> -f data.sql
-- 所有 INSERT 均带 WHERE NOT EXISTS，可安全重复执行
-- ============================================================

-- ────────────────────────────────────────
-- 1. 管理员账户 (密码: admin123，请首次登录后立即修改)
--    默认使用 BCrypt 哈希。若 application.yml 设置了 security.password-plain: true，
--    则需将下方 password_hash 改为明文 'admin123'
-- ────────────────────────────────────────
INSERT INTO users (id, username, email, password_hash, role, points, is_deleted, failed_login_attempts, lock_until, created_at, updated_at)
SELECT gen_random_uuid(), 'admin', 'admin@example.com',
       'admin123',
       'ADMIN', 0, 0, 0, NULL, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM users WHERE username = 'admin');

-- ────────────────────────────────────────
-- 2. 站点配置 (config_group = 'site')
-- ────────────────────────────────────────

-- 站点名称，显示在页面标题和 Header
INSERT INTO site_configs (id, config_key, config_value, config_group, created_at, updated_at)
SELECT gen_random_uuid(), 'site_name', 'Orion Key', 'site', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM site_configs WHERE config_key = 'site_name');

-- 站点标语，显示在首页 Hero 区域
INSERT INTO site_configs (id, config_key, config_value, config_group, created_at, updated_at)
SELECT gen_random_uuid(), 'site_slogan', 'Instant Keys, Anytime', 'site', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM site_configs WHERE config_key = 'site_slogan');

-- 站点描述，显示在首页副标题 / SEO
INSERT INTO site_configs (id, config_key, config_value, config_group, created_at, updated_at)
SELECT gen_random_uuid(), 'site_description', 'Automated delivery, available 24/7.', 'site', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM site_configs WHERE config_key = 'site_description');

-- 页脚（留空则不显示）
INSERT INTO site_configs (id, config_key, config_value, config_group, created_at, updated_at)
SELECT gen_random_uuid(), 'footer_text', '由开源 Orion Key 提供服务', 'site', NOW(), NOW()
    WHERE NOT EXISTS (SELECT 1 FROM site_configs WHERE config_key = 'footer_text');

INSERT INTO site_configs (id, config_key, config_value, config_group, created_at, updated_at)
SELECT gen_random_uuid(), 'github_url', 'https://github.com/bearhero123/OnlineShop', 'site', NOW(), NOW()
    WHERE NOT EXISTS (SELECT 1 FROM site_configs WHERE config_key = 'github_url');

-- 积分功能总开关 (true/false)
INSERT INTO site_configs (id, config_key, config_value, config_group, created_at, updated_at)
SELECT gen_random_uuid(), 'points_enabled', 'false', 'site', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM site_configs WHERE config_key = 'points_enabled');

-- 积分倍率：每消费 1 元获得的积分数
INSERT INTO site_configs (id, config_key, config_value, config_group, created_at, updated_at)
SELECT gen_random_uuid(), 'points_rate', '1', 'site', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM site_configs WHERE config_key = 'points_rate');

-- 维护模式开关，开启后非管理员请求返回 503 (true/false)
INSERT INTO site_configs (id, config_key, config_value, config_group, created_at, updated_at)
SELECT gen_random_uuid(), 'maintenance_enabled', 'false', 'site', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM site_configs WHERE config_key = 'maintenance_enabled');

-- 维护模式提示文案
INSERT INTO site_configs (id, config_key, config_value, config_group, created_at, updated_at)
SELECT gen_random_uuid(), 'maintenance_message', '', 'site', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM site_configs WHERE config_key = 'maintenance_message');

-- 全站公告开关 (true/false)
INSERT INTO site_configs (id, config_key, config_value, config_group, created_at, updated_at)
SELECT gen_random_uuid(), 'announcement_enabled', 'false', 'site', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM site_configs WHERE config_key = 'announcement_enabled');

-- 首页公告内容（支持管理员随时更新）
INSERT INTO site_configs (id, config_key, config_value, config_group, created_at, updated_at)
SELECT gen_random_uuid(), 'announcement', '', 'site', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM site_configs WHERE config_key = 'announcement');

-- 弹窗通知开关 (true/false)
INSERT INTO site_configs (id, config_key, config_value, config_group, created_at, updated_at)
SELECT gen_random_uuid(), 'popup_enabled', 'false', 'site', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM site_configs WHERE config_key = 'popup_enabled');

-- 弹窗通知内容
INSERT INTO site_configs (id, config_key, config_value, config_group, created_at, updated_at)
SELECT gen_random_uuid(), 'popup_content', '', 'site', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM site_configs WHERE config_key = 'popup_content');

-- ────────────────────────────────────────
-- 3. 风控配置 (config_group = 'risk')
-- ────────────────────────────────────────

-- 单 IP 每秒最大请求数（令牌桶容量）
INSERT INTO site_configs (id, config_key, config_value, config_group, created_at, updated_at)
SELECT gen_random_uuid(), 'rate_limit_per_second', '25', 'risk', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM site_configs WHERE config_key = 'rate_limit_per_second');

-- 单账号连续登录失败上限（超过后需等待冷却）
INSERT INTO site_configs (id, config_key, config_value, config_group, created_at, updated_at)
SELECT gen_random_uuid(), 'login_attempt_limit', '10', 'risk', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM site_configs WHERE config_key = 'login_attempt_limit');

-- 每用户单次最大购买数量
INSERT INTO site_configs (id, config_key, config_value, config_group, created_at, updated_at)
SELECT gen_random_uuid(), 'max_purchase_per_user', '50', 'risk', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM site_configs WHERE config_key = 'max_purchase_per_user');

-- 单 IP 最大未支付订单数（防刷单，共享 IP 场景适当放宽）
INSERT INTO site_configs (id, config_key, config_value, config_group, created_at, updated_at)
SELECT gen_random_uuid(), 'max_pending_orders_per_ip', '5', 'risk', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM site_configs WHERE config_key = 'max_pending_orders_per_ip');

-- 单用户最大未支付订单数
INSERT INTO site_configs (id, config_key, config_value, config_group, created_at, updated_at)
SELECT gen_random_uuid(), 'max_pending_orders_per_user', '5', 'risk', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM site_configs WHERE config_key = 'max_pending_orders_per_user');

-- 未支付订单自动过期时间（分钟）
INSERT INTO site_configs (id, config_key, config_value, config_group, created_at, updated_at)
SELECT gen_random_uuid(), 'order_expire_minutes', '15', 'risk', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM site_configs WHERE config_key = 'order_expire_minutes');

-- Turnstile 人机验证开关（默认关闭，需后台手动启用）
INSERT INTO site_configs (id, config_key, config_value, config_group, created_at, updated_at)
SELECT gen_random_uuid(), 'turnstile_enabled', 'false', 'risk', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM site_configs WHERE config_key = 'turnstile_enabled');

-- 设备指纹限流开关（默认关闭，需后台手动启用）
INSERT INTO site_configs (id, config_key, config_value, config_group, created_at, updated_at)
SELECT gen_random_uuid(), 'device_rate_limit_enabled', 'false', 'risk', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM site_configs WHERE config_key = 'device_rate_limit_enabled');

-- 设备指纹限流：下单频率上限（次/小时/设备）
INSERT INTO site_configs (id, config_key, config_value, config_group, created_at, updated_at)
SELECT gen_random_uuid(), 'device_order_limit_per_hour', '15', 'risk', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM site_configs WHERE config_key = 'device_order_limit_per_hour');

-- 设备指纹限流：查询频率上限（次/小时/设备）
INSERT INTO site_configs (id, config_key, config_value, config_group, created_at, updated_at)
SELECT gen_random_uuid(), 'device_query_limit_per_hour', '50', 'risk', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM site_configs WHERE config_key = 'device_query_limit_per_hour');

-- 设备指纹限流：登录频率上限（次/小时/设备）
INSERT INTO site_configs (id, config_key, config_value, config_group, created_at, updated_at)
SELECT gen_random_uuid(), 'device_login_limit_per_hour', '10', 'risk', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM site_configs WHERE config_key = 'device_login_limit_per_hour');

-- 设备指纹限流：注册频率上限（次/小时/设备）
INSERT INTO site_configs (id, config_key, config_value, config_group, created_at, updated_at)
SELECT gen_random_uuid(), 'device_register_limit_per_hour', '10', 'risk', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM site_configs WHERE config_key = 'device_register_limit_per_hour');


-- ────────────────────────────────────────
-- 4. 货币类型
-- ────────────────────────────────────────
DELETE FROM site_configs
WHERE config_key IN ('device_txid_limit_per_hour', 'txid_submit_limit_per_order');

DELETE FROM payment_channels
WHERE LOWER(provider_type) NOT IN ('epay', 'native_alipay', 'native_wxpay')
   OR LOWER(channel_code) LIKE 'usdt%';

DELETE FROM currencies
WHERE UPPER(code) = 'USDT';

INSERT INTO currencies (id, code, name, symbol, is_enabled, sort_order, created_at, updated_at)
SELECT gen_random_uuid(), 'CNY', '人民币', '¥', true, 1, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM currencies WHERE code = 'CNY');

INSERT INTO currencies (id, code, name, symbol, is_enabled, sort_order, created_at, updated_at)
SELECT gen_random_uuid(), 'USD', '美元', '$', true, 2, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM currencies WHERE code = 'USD');

INSERT INTO currencies (id, code, name, symbol, is_enabled, sort_order, created_at, updated_at)
SELECT gen_random_uuid(), 'EUR', '欧元', '€', true, 3, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM currencies WHERE code = 'EUR');

INSERT INTO currencies (id, code, name, symbol, is_enabled, sort_order, created_at, updated_at)
SELECT gen_random_uuid(), 'GBP', '英镑', '£', true, 4, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM currencies WHERE code = 'GBP');

-- ────────────────────────────────────────
-- 5. 公开仓库说明
--    为避免泄露业务资料，公开版本不预置任何商品、卡密或演示库存。
--    如需本地演示，请在部署后自行通过后台创建分类、商品与库存。
-- ────────────────────────────────────────

commit;
