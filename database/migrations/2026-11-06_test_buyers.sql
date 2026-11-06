-- Test-buyer accounts and test orders (simulated payments on the live site, for chosen accounts only).
-- users.test_buyer = 1: a super admin ticked "Test buyer" in Admin > People; that account sees "Simulate payment".
-- is_test = 1 on an order: paid by simulation; kept out of revenue, the ledger, payouts and the reconciler.
ALTER TABLE users ADD COLUMN test_buyer TINYINT NOT NULL DEFAULT 0;
ALTER TABLE orders ADD COLUMN is_test TINYINT NOT NULL DEFAULT 0;
ALTER TABLE marketplace_orders ADD COLUMN is_test TINYINT NOT NULL DEFAULT 0;
ALTER TABLE shop_orders ADD COLUMN is_test TINYINT NOT NULL DEFAULT 0;
ALTER TABLE commission_orders ADD COLUMN is_test TINYINT NOT NULL DEFAULT 0;
-- orders simulated before this column existed
UPDATE marketplace_orders SET is_test = 1 WHERE gateway = 'TEST';
UPDATE shop_orders SET is_test = 1 WHERE gateway = 'TEST';
UPDATE orders SET is_test = 1 WHERE payment_id LIKE 'test\_%';
UPDATE commission_orders SET is_test = 1 WHERE payment_transaction_id LIKE 'TEST-%';
