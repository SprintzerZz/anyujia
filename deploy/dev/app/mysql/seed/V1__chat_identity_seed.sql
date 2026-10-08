-- Development data for tenant/landlord chat testing.
-- Prerequisite: app_user exists and migration/V4__app_user_identity.sql has run.

START TRANSACTION;

INSERT INTO `app_user`
    (`open_id`, `phone_number`, `nickname`, `avatar_url`, `gender`, `status`, `created_at`, `updated_at`)
SELECT
    'dev_chat_tenant_001', '19900000001', 'Dev Tenant', '', 0, 1,
    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (
    SELECT 1
    FROM `app_user`
    WHERE `open_id` = 'dev_chat_tenant_001'
);

INSERT INTO `app_user`
    (`open_id`, `phone_number`, `nickname`, `avatar_url`, `gender`, `status`, `created_at`, `updated_at`)
SELECT
    'dev_chat_landlord_001', '19900000002', 'Dev Landlord', '', 0, 1,
    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (
    SELECT 1
    FROM `app_user`
    WHERE `open_id` = 'dev_chat_landlord_001'
);

SET @tenant_app_user_id := (
    SELECT `id`
    FROM `app_user`
    WHERE `open_id` = 'dev_chat_tenant_001'
    ORDER BY `id`
    LIMIT 1
);

SET @landlord_app_user_id := (
    SELECT `id`
    FROM `app_user`
    WHERE `open_id` = 'dev_chat_landlord_001'
    ORDER BY `id`
    LIMIT 1
);

INSERT IGNORE INTO `tenant_user`
    (`app_user_id`, `status`, `created_at`, `updated_at`)
SELECT @tenant_app_user_id, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE @tenant_app_user_id IS NOT NULL;

UPDATE `tenant_user`
SET `status` = 1,
    `updated_at` = CURRENT_TIMESTAMP
WHERE `app_user_id` = @tenant_app_user_id;

-- Identities are additive: a landlord account can also act as a tenant.
INSERT IGNORE INTO `tenant_user`
    (`app_user_id`, `status`, `created_at`, `updated_at`)
SELECT @landlord_app_user_id, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE @landlord_app_user_id IS NOT NULL;

UPDATE `tenant_user`
SET `status` = 1,
    `updated_at` = CURRENT_TIMESTAMP
WHERE `app_user_id` = @landlord_app_user_id;

INSERT IGNORE INTO `landlord_user`
    (`app_user_id`, `verify_status`, `status`, `created_at`, `updated_at`)
SELECT @landlord_app_user_id, 2, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE @landlord_app_user_id IS NOT NULL;

UPDATE `landlord_user`
SET `verify_status` = 2,
    `status` = 1,
    `updated_at` = CURRENT_TIMESTAMP
WHERE `app_user_id` = @landlord_app_user_id;

COMMIT;

SELECT
    u.`id` AS `app_user_id`,
    u.`open_id`,
    u.`phone_number`,
    u.`nickname`,
    u.`status` AS `app_user_status`,
    t.`status` AS `tenant_status`,
    l.`verify_status` AS `landlord_verify_status`,
    l.`status` AS `landlord_status`
FROM `app_user` u
LEFT JOIN `tenant_user` t ON t.`app_user_id` = u.`id`
LEFT JOIN `landlord_user` l ON l.`app_user_id` = u.`id`
WHERE u.`open_id` IN ('dev_chat_tenant_001', 'dev_chat_landlord_001')
ORDER BY u.`id`;
