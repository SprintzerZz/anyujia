-- Development house publication owned by the approved landlord test account.
-- Prerequisite: migration/V5__house_publish.sql and seed/V1__chat_identity_seed.sql.

START TRANSACTION;

SET @landlord_app_user_id := (
    SELECT `id`
    FROM `app_user`
    WHERE `open_id` = 'dev_chat_landlord_001'
      AND `status` = 1
    ORDER BY `id`
    LIMIT 1
);

INSERT INTO `house`
    (`house_code`, `landlord_app_user_id`, `title`, `city`, `district`, `address`,
     `monthly_rent`, `status`, `created_at`, `updated_at`)
SELECT
    'DEV-CHAT-HOUSE-001', @landlord_app_user_id, 'Dev Chat Apartment',
    'Shenzhen', 'Nanshan', 'Dev Test Address', 3500.00, 1,
    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE @landlord_app_user_id IS NOT NULL
  AND NOT EXISTS (
      SELECT 1
      FROM `house`
      WHERE `house_code` = 'DEV-CHAT-HOUSE-001'
  );

SET @house_id := (
    SELECT `id`
    FROM `house`
    WHERE `house_code` = 'DEV-CHAT-HOUSE-001'
    ORDER BY `id`
    LIMIT 1
);

SET @house_publish_id := 900000000000000001;

INSERT INTO `house_publish`
    (`id`, `house_id`, `landlord_user_id`, `status`, `audit_status`, `deleted`)
SELECT
    @house_publish_id, @house_id, @landlord_app_user_id, 1, 2, 0
WHERE @house_id IS NOT NULL
  AND @landlord_app_user_id IS NOT NULL
  AND NOT EXISTS (
      SELECT 1
      FROM `house_publish`
      WHERE `id` = @house_publish_id
  );

COMMIT;

SELECT
    hp.`id` AS `house_publish_id`,
    h.`id` AS `house_id`,
    hp.`landlord_user_id` AS `landlord_app_user_id`,
    au.`phone_number` AS `landlord_phone`,
    h.`title`,
    hp.`status` AS `publish_status`,
    hp.`audit_status`,
    hp.`deleted`
FROM `house_publish` hp
JOIN `house` h ON h.`id` = hp.`house_id`
JOIN `app_user` au ON au.`id` = hp.`landlord_user_id`
WHERE hp.`id` = @house_publish_id;
