-- ═══════════════════════════════════════════════════════════════════════
-- Cash on Delivery: every order now says HOW it is paid.
--
-- payment_mode   COD     the customer pays the delivery partner at the door.
--                        No Razorpay payment exists for the order.
--                ONLINE  the customer paid through Razorpay (UPI / card)
--                        before the order was created (payment-first).
--
-- This is the column the code branches on. The existing free-text column
-- payment_method_id keeps the label the app sent (COD / UPI / CARD / ONLINE)
-- and is only used for display.
--
-- Backfill: an order that has a payment line was paid through Razorpay, so it
-- is ONLINE. An order with no payment line was created before the Razorpay
-- integration existed; the app has always shown those as COD, so they become
-- COD here and keep showing the same thing.
-- ═══════════════════════════════════════════════════════════════════════

ALTER TABLE orders
    ADD COLUMN IF NOT EXISTS payment_mode VARCHAR(10) NOT NULL DEFAULT 'ONLINE'
        CHECK (payment_mode IN ('COD', 'ONLINE'));

UPDATE orders o
   SET payment_mode = 'COD'
 WHERE NOT EXISTS (SELECT 1 FROM payment_order_lines l WHERE l.order_id = o.id);
