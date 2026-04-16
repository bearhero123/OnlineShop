CREATE EXTENSION IF NOT EXISTS pgcrypto;

\i /seed/base-data.sql

DELETE FROM site_configs
WHERE config_key IN ('device_txid_limit_per_hour', 'txid_submit_limit_per_order');

DROP TABLE IF EXISTS unmatched_transactions;

ALTER TABLE IF EXISTS orders DROP COLUMN IF EXISTS usdt_wallet_address;
ALTER TABLE IF EXISTS orders DROP COLUMN IF EXISTS usdt_crypto_amount;
ALTER TABLE IF EXISTS orders DROP COLUMN IF EXISTS usdt_trade_id;
ALTER TABLE IF EXISTS orders DROP COLUMN IF EXISTS usdt_chain;
ALTER TABLE IF EXISTS orders DROP COLUMN IF EXISTS usdt_tx_id;

INSERT INTO payment_channels (id, channel_code, channel_name, provider_type, config_data, is_enabled, sort_order, is_deleted, created_at, updated_at)
SELECT gen_random_uuid(),
       'wechat',
       '微信支付',
       'epay',
       '{"pid":"1000001","key":"orion-key-local-epay-key","api_url":"http://mock-epay:18080/","notify_url":"http://api:8083/api/payments/webhook/epay","return_url":"http://localhost:3000/order/query"}',
       true,
       1,
       0,
       NOW(),
       NOW()
WHERE NOT EXISTS (
    SELECT 1 FROM payment_channels WHERE channel_code = 'wechat' AND provider_type = 'epay' AND is_deleted = 0
);

INSERT INTO payment_channels (id, channel_code, channel_name, provider_type, config_data, is_enabled, sort_order, is_deleted, created_at, updated_at)
SELECT gen_random_uuid(),
       'alipay',
       '支付宝',
       'epay',
       '{"pid":"1000001","key":"orion-key-local-epay-key","api_url":"http://mock-epay:18080/","notify_url":"http://api:8083/api/payments/webhook/epay","return_url":"http://localhost:3000/order/query"}',
       true,
       2,
       0,
       NOW(),
       NOW()
WHERE NOT EXISTS (
    SELECT 1 FROM payment_channels WHERE channel_code = 'alipay' AND provider_type = 'epay' AND is_deleted = 0
);

UPDATE users
SET password_hash = 'admin123'
WHERE username = 'admin';
