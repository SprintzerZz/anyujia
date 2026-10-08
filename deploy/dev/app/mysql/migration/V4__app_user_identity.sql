CREATE TABLE IF NOT EXISTS `tenant_user`
(
    `id`          bigint   NOT NULL AUTO_INCREMENT,
    `app_user_id` bigint   NOT NULL COMMENT 'Unified account ID, references app_user.id',
    `status`      tinyint  NOT NULL DEFAULT 1 COMMENT '0 disabled, 1 enabled',
    `created_at`  datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`  datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_tenant_user_app_user` (`app_user_id`),
    KEY `idx_tenant_user_status` (`status`, `app_user_id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='Tenant identity';

CREATE TABLE IF NOT EXISTS `landlord_user`
(
    `id`            bigint   NOT NULL AUTO_INCREMENT,
    `app_user_id`   bigint   NOT NULL COMMENT 'Unified account ID, references app_user.id',
    `verify_status` tinyint  NOT NULL DEFAULT 0 COMMENT '0 pending, 1 reviewing, 2 approved, 3 rejected',
    `status`        tinyint  NOT NULL DEFAULT 1 COMMENT '0 disabled, 1 enabled',
    `created_at`    datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`    datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_landlord_user_app_user` (`app_user_id`),
    KEY `idx_landlord_user_verify_status` (`verify_status`, `status`, `app_user_id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='Landlord identity';

-- Existing C-end accounts receive a tenant identity. Landlord identities must be
-- created by the landlord onboarding flow and only become usable after approval.
INSERT IGNORE INTO `tenant_user` (`app_user_id`, `status`, `created_at`, `updated_at`)
SELECT `id`, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM `app_user`;
