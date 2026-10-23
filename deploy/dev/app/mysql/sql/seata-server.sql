-- Seata server store (run once as root on the main MySQL, or on first init).
-- Holds the TC's global/branch/lock tables in a dedicated database.
CREATE DATABASE IF NOT EXISTS `frameworkjava_seata`
  DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;

GRANT ALL PRIVILEGES ON `frameworkjava_seata`.* TO 'appdev'@'%';
FLUSH PRIVILEGES;

USE `frameworkjava_seata`;

CREATE TABLE IF NOT EXISTS `global_table` (
  `xid`                       VARCHAR(128) NOT NULL,
  `transaction_id`            BIGINT       NOT NULL,
  `status`                    TINYINT      NOT NULL,
  `application_id`            VARCHAR(64)  DEFAULT NULL,
  `transaction_service_group` VARCHAR(64)  DEFAULT NULL,
  `transaction_name`          VARCHAR(128) DEFAULT NULL,
  `timeout`                   INT          DEFAULT NULL,
  `begin_time`                BIGINT       DEFAULT NULL,
  `application_data`          VARCHAR(2000) DEFAULT NULL,
  `gmt_create`                DATETIME     DEFAULT NULL,
  `gmt_modified`              DATETIME     DEFAULT NULL,
  PRIMARY KEY (`xid`),
  KEY `idx_status_gmt_modified` (`status`, `gmt_modified`),
  KEY `idx_transaction_id` (`transaction_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE IF NOT EXISTS `branch_table` (
  `branch_id`         BIGINT       NOT NULL,
  `xid`               VARCHAR(128) NOT NULL,
  `transaction_id`    BIGINT       DEFAULT NULL,
  `resource_group_id` VARCHAR(32)  DEFAULT NULL,
  `resource_id`       VARCHAR(256) DEFAULT NULL,
  `branch_type`       VARCHAR(8)   DEFAULT NULL,
  `status`            TINYINT      DEFAULT NULL,
  `client_id`         VARCHAR(64)  DEFAULT NULL,
  `application_data`  VARCHAR(2000) DEFAULT NULL,
  `gmt_create`        DATETIME(6)  DEFAULT NULL,
  `gmt_modified`      DATETIME(6)  DEFAULT NULL,
  PRIMARY KEY (`branch_id`),
  KEY `idx_xid` (`xid`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE IF NOT EXISTS `lock_table` (
  `row_key`        VARCHAR(128) NOT NULL,
  `xid`            VARCHAR(128) DEFAULT NULL,
  `transaction_id` BIGINT       DEFAULT NULL,
  `branch_id`      BIGINT       DEFAULT NULL,
  `resource_id`    VARCHAR(256) DEFAULT NULL,
  `table_name`     VARCHAR(32)  DEFAULT NULL,
  `pk`             VARCHAR(36)  DEFAULT NULL,
  `status`         TINYINT      NOT NULL DEFAULT '0',
  `gmt_create`     DATETIME     DEFAULT NULL,
  `gmt_modified`   DATETIME     DEFAULT NULL,
  PRIMARY KEY (`row_key`),
  KEY `idx_branch_id` (`branch_id`),
  KEY `idx_xid_and_branch_id` (`xid`, `branch_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE IF NOT EXISTS `distributed_lock` (
  `lock_key`   VARCHAR(20) NOT NULL,
  `lock_value` VARCHAR(128) NOT NULL,
  `expire`     BIGINT      DEFAULT NULL,
  PRIMARY KEY (`lock_key`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
