-- Minimal house and publication model required by tenant-landlord conversations.
-- Cross-service ownership is stored with the unified app_user ID.
CREATE TABLE IF NOT EXISTS `house`
(
    `id`                     bigint         NOT NULL AUTO_INCREMENT COMMENT 'House ID',
    `house_code`             varchar(64)    NOT NULL COMMENT 'Stable house code',
    `landlord_app_user_id`   bigint         NOT NULL COMMENT 'Owner app_user.id',
    `title`                  varchar(128)   NOT NULL COMMENT 'House title',
    `city`                   varchar(64)    NOT NULL DEFAULT '' COMMENT 'City',
    `district`               varchar(64)    NOT NULL DEFAULT '' COMMENT 'District',
    `address`                varchar(255)   NOT NULL DEFAULT '' COMMENT 'Address',
    `monthly_rent`           decimal(12, 2) NOT NULL DEFAULT 0.00 COMMENT 'Monthly rent',
    `status`                 tinyint        NOT NULL DEFAULT 1 COMMENT '0 disabled, 1 enabled',
    `created_at`             datetime       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`             datetime       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_house_code` (`house_code`),
    KEY `idx_house_landlord_status` (`landlord_app_user_id`, `status`, `id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='House';

CREATE TABLE IF NOT EXISTS `house_publish`
(
    `id`                     bigint       NOT NULL COMMENT 'House publication ID',
    `house_id`               bigint       NOT NULL COMMENT 'house.id',
    `landlord_user_id`       bigint       NOT NULL COMMENT 'Publisher app_user.id',
    `status`                 tinyint      NOT NULL COMMENT '0 draft, 1 published, 2 offline, 3 expired',
    `audit_status`           tinyint      NOT NULL COMMENT '0 pending, 1 reviewing, 2 approved, 3 rejected',
    `deleted`                tinyint      NOT NULL DEFAULT 0 COMMENT '0 active, 1 deleted',
    PRIMARY KEY (`id`),
    KEY `idx_house_publish_house_status` (`house_id`, `status`, `id`),
    KEY `idx_publish_landlord` (`landlord_user_id`, `status`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='House publication record';
