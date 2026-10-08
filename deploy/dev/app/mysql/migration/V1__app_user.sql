-- Unified C-end account. A user may sign in by WeChat open_id or phone number.
CREATE TABLE IF NOT EXISTS `app_user`
(
    `id`           bigint       NOT NULL AUTO_INCREMENT COMMENT 'Unified C-end user ID',
    `open_id`      varchar(128) COLLATE utf8mb4_bin DEFAULT NULL COMMENT 'WeChat openId',
    `phone_number` varchar(32)            DEFAULT NULL COMMENT 'Phone number',
    `nickname`     varchar(64)  NOT NULL COMMENT 'Nickname',
    `avatar_url`   varchar(512) NOT NULL DEFAULT '' COMMENT 'Avatar URL',
    `gender`       tinyint      NOT NULL DEFAULT 0 COMMENT '0 unknown, 1 male, 2 female',
    `status`       tinyint      NOT NULL DEFAULT 1 COMMENT '0 disabled, 1 enabled',
    `created_at`   datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`   datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_app_user_open_id` (`open_id`),
    UNIQUE KEY `uk_app_user_phone_number` (`phone_number`),
    KEY `idx_app_user_status_id` (`status`, `id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COMMENT ='Unified C-end user account';
