-- Coupon expiry support and optional ShardingSphere physical tables.

DROP PROCEDURE IF EXISTS create_coupon_sharding_tables;

DELIMITER //
CREATE PROCEDURE create_coupon_sharding_tables()
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.tables
        WHERE table_schema = DATABASE()
          AND table_name = 'coupon_template_0'
    ) THEN
        CREATE TABLE `coupon_template_0` LIKE `coupon_template`;
    END IF;
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.tables
        WHERE table_schema = DATABASE()
          AND table_name = 'coupon_template_1'
    ) THEN
        CREATE TABLE `coupon_template_1` LIKE `coupon_template`;
    END IF;
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.tables
        WHERE table_schema = DATABASE()
          AND table_name = 'user_coupon_0'
    ) THEN
        CREATE TABLE `user_coupon_0` LIKE `user_coupon`;
    END IF;
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.tables
        WHERE table_schema = DATABASE()
          AND table_name = 'user_coupon_1'
    ) THEN
        CREATE TABLE `user_coupon_1` LIKE `user_coupon`;
    END IF;
END //
DELIMITER ;

CALL create_coupon_sharding_tables();
DROP PROCEDURE IF EXISTS create_coupon_sharding_tables;
