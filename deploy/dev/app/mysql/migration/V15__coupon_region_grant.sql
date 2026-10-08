-- Region coupon and batch grant support.

DROP PROCEDURE IF EXISTS add_coupon_template_column;

DELIMITER //
CREATE PROCEDURE add_coupon_template_column(
    IN p_column_name varchar(64),
    IN p_column_definition varchar(512)
)
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = 'coupon_template'
          AND column_name = p_column_name
    ) THEN
        SET @ddl = CONCAT('ALTER TABLE `coupon_template` ADD COLUMN ', p_column_definition);
        PREPARE stmt FROM @ddl;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END //
DELIMITER ;

CALL add_coupon_template_column('scope_type', '`scope_type` tinyint NOT NULL DEFAULT 1 COMMENT ''scope: 1 platform, 2 region, 3 national subsidy'' AFTER `applicable_user_type`');
CALL add_coupon_template_column('province_code', '`province_code` varchar(16) DEFAULT NULL COMMENT ''province code'' AFTER `scope_type`');
CALL add_coupon_template_column('city_code', '`city_code` varchar(16) DEFAULT NULL COMMENT ''city code'' AFTER `province_code`');
CALL add_coupon_template_column('district_code', '`district_code` varchar(16) DEFAULT NULL COMMENT ''district code'' AFTER `city_code`');
CALL add_coupon_template_column('policy_code', '`policy_code` varchar(64) DEFAULT NULL COMMENT ''policy code'' AFTER `district_code`');
CALL add_coupon_template_column('grant_mode', '`grant_mode` tinyint NOT NULL DEFAULT 1 COMMENT ''grant mode: 1 public, 2 whitelist'' AFTER `policy_code`');
CALL add_coupon_template_column('receive_limit', '`receive_limit` int NOT NULL DEFAULT 1 COMMENT ''single user receive limit'' AFTER `grant_mode`');

DROP PROCEDURE IF EXISTS add_coupon_template_column;

DROP PROCEDURE IF EXISTS ensure_user_coupon_receive_request_id;

DELIMITER //
CREATE PROCEDURE ensure_user_coupon_receive_request_id()
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = 'user_coupon'
          AND column_name = 'receive_request_id'
    ) THEN
        ALTER TABLE `user_coupon`
            ADD COLUMN `receive_request_id` varchar(80) DEFAULT NULL COMMENT 'async receive request id' AFTER `template_id`;
    ELSEIF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = 'user_coupon'
          AND column_name = 'receive_request_id'
          AND character_maximum_length < 80
    ) THEN
        ALTER TABLE `user_coupon`
            MODIFY COLUMN `receive_request_id` varchar(80) DEFAULT NULL COMMENT 'async receive request id';
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.statistics
        WHERE table_schema = DATABASE()
          AND table_name = 'user_coupon'
          AND index_name = 'uk_user_coupon_receive_request'
    ) THEN
        ALTER TABLE `user_coupon`
            ADD UNIQUE KEY `uk_user_coupon_receive_request` (`receive_request_id`);
    END IF;
END //
DELIMITER ;

CALL ensure_user_coupon_receive_request_id();
DROP PROCEDURE IF EXISTS ensure_user_coupon_receive_request_id;

CREATE TABLE IF NOT EXISTS `coupon_grant_task`
(
    `id`            bigint       NOT NULL AUTO_INCREMENT COMMENT 'batch grant task id',
    `template_id`   bigint       NOT NULL COMMENT 'coupon template id',
    `user_type`     tinyint      NOT NULL COMMENT 'grant user type',
    `total_count`   int          NOT NULL DEFAULT 0 COMMENT 'total user count',
    `success_count` int          NOT NULL DEFAULT 0 COMMENT 'success count',
    `failure_count` int          NOT NULL DEFAULT 0 COMMENT 'failure count',
    `status`        tinyint      NOT NULL DEFAULT 0 COMMENT 'status: 0 pending, 1 processing, 2 completed, 3 failed',
    `remark`        varchar(512)          DEFAULT NULL COMMENT 'remark',
    `created_at`    datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`    datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_coupon_grant_task_status_time` (`status`, `updated_at`),
    KEY `idx_coupon_grant_task_template` (`template_id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='coupon batch grant task';

CREATE TABLE IF NOT EXISTS `coupon_grant_fail_record`
(
    `id`          bigint       NOT NULL AUTO_INCREMENT COMMENT 'failure record id',
    `task_id`     bigint       NOT NULL COMMENT 'batch grant task id',
    `template_id` bigint       NOT NULL COMMENT 'coupon template id',
    `user_id`     bigint       NOT NULL COMMENT 'user id',
    `user_type`   tinyint      NOT NULL COMMENT 'user type',
    `reason`      varchar(255) NOT NULL COMMENT 'failure reason',
    `created_at`  datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_coupon_grant_fail_task` (`task_id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='coupon batch grant failure record';

DROP PROCEDURE IF EXISTS add_coupon_index_if_missing;

DELIMITER //
CREATE PROCEDURE add_coupon_index_if_missing()
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.statistics
        WHERE table_schema = DATABASE()
          AND table_name = 'coupon_template'
          AND index_name = 'idx_coupon_template_region'
    ) THEN
        ALTER TABLE `coupon_template`
            ADD KEY `idx_coupon_template_region` (`scope_type`, `province_code`, `city_code`, `district_code`);
    END IF;
END //
DELIMITER ;

CALL add_coupon_index_if_missing();
DROP PROCEDURE IF EXISTS add_coupon_index_if_missing;
