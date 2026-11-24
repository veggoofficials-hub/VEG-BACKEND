-- ============================================================
-- V177 -- Order snapshot: an order is a photograph of the moment it was placed.
--
-- Before this, the shop's name/address/phone, the product's name/image and the
-- customer's name/phone were looked up LIVE every time an order was opened, so a
-- renamed or deleted shop/product (or a purged customer) changed or blanked old
-- orders. They are now copied onto the order:
--   at checkout        -> customer name + phone, every item's product name + image
--   when a shop accepts -> shop name, address, business phone, owner name
-- Prices, totals, fees, the delivery address and the rider's details were already frozen.
--
-- Backfill: existing orders get TODAY's values (past renames cannot be recovered).
-- Deleted shops / products / customers are included on purpose.
-- ============================================================

ALTER TABLE orders ADD COLUMN IF NOT EXISTS customer_name            VARCHAR(255);
ALTER TABLE orders ADD COLUMN IF NOT EXISTS customer_phone           VARCHAR(64);
ALTER TABLE orders ADD COLUMN IF NOT EXISTS accepted_shop_name       VARCHAR(255);
ALTER TABLE orders ADD COLUMN IF NOT EXISTS accepted_shop_address    TEXT;
ALTER TABLE orders ADD COLUMN IF NOT EXISTS accepted_shop_phone      VARCHAR(64);
ALTER TABLE orders ADD COLUMN IF NOT EXISTS accepted_shop_owner_name VARCHAR(255);

ALTER TABLE order_items ADD COLUMN IF NOT EXISTS product_name      VARCHAR(255);
ALTER TABLE order_items ADD COLUMN IF NOT EXISTS product_image_url VARCHAR(1000);

-- customer
UPDATE orders o
   SET customer_name = cp.full_name
  FROM customer_profiles cp
 WHERE cp.user_id = o.user_id
   AND o.customer_name IS NULL
   AND cp.full_name IS NOT NULL;

UPDATE orders o
   SET customer_phone = u.phone
  FROM users u
 WHERE u.id = o.user_id
   AND o.customer_phone IS NULL
   AND u.phone NOT LIKE 'deleted\_%'
   AND LENGTH(u.phone) <= 64;

-- accepted shop
UPDATE orders o
   SET accepted_shop_name       = s.name,
       accepted_shop_address    = s.address,
       accepted_shop_phone      = s.business_phone,
       accepted_shop_owner_name = s.full_name
  FROM vendor_shops s
 WHERE s.id = o.accepted_shop_id
   AND o.accepted_shop_name IS NULL;

-- items
UPDATE order_items oi
   SET product_name      = cp.name,
       product_image_url = cp.image_url
  FROM catalog_products cp
 WHERE cp.id = oi.product_id
   AND oi.product_name IS NULL;
