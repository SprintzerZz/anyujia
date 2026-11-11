-- Development-only fixture for 100 distinct tenants consulting one published listing.
-- Reserved app_user IDs: 940000000000000001..100.
-- Reserved conversation IDs: 941000000000000001..100.
-- Safe to rerun: existing messages, unread counts, and conversations are preserved.
-- Requires seed/V1__chat_identity_seed.sql and seed/V2__chat_house_seed.sql.

START TRANSACTION;

INSERT IGNORE INTO app_user
    (id, open_id, phone_number, nickname, avatar_url, gender, status)
SELECT 940000000000000000 + numbers.n,
       CONCAT('perf_hot_tenant_', LPAD(numbers.n, 3, '0')),
       NULL, CONCAT('Perf Tenant ', numbers.n), '', 0, 1
FROM (
    SELECT ones.n + tens.n * 10 + 1 AS n
    FROM (SELECT 0 n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4
          UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) ones
    CROSS JOIN
         (SELECT 0 n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4
          UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) tens
) numbers;

INSERT IGNORE INTO tenant_user (app_user_id, status)
SELECT id, 1 FROM app_user
WHERE id BETWEEN 940000000000000001 AND 940000000000000100
  AND open_id LIKE 'perf_hot_tenant_%';

INSERT IGNORE INTO chat_conversation
    (id, tenant_user_id, landlord_user_id, house_id, house_publish_id, status)
SELECT 941000000000000000 + (u.id - 940000000000000000),
       u.id, hp.landlord_user_id, hp.house_id, hp.id, 1
FROM app_user u
JOIN house_publish hp ON hp.id = 900000000000000001
JOIN app_user landlord ON landlord.id = hp.landlord_user_id
    AND landlord.open_id = 'dev_chat_landlord_001'
WHERE u.id BETWEEN 940000000000000001 AND 940000000000000100
  AND u.open_id LIKE 'perf_hot_tenant_%';

INSERT IGNORE INTO chat_conversation_member (conversation_id, user_id, role_type)
SELECT id, tenant_user_id, 1 FROM chat_conversation
WHERE id BETWEEN 941000000000000001 AND 941000000000000100;

INSERT IGNORE INTO chat_conversation_member (conversation_id, user_id, role_type)
SELECT id, landlord_user_id, 2 FROM chat_conversation
WHERE id BETWEEN 941000000000000001 AND 941000000000000100;

INSERT IGNORE INTO chat_user_conversation
    (user_id, conversation_id, role_type, other_user_id, house_id, house_publish_id, status)
SELECT tenant_user_id, id, 1, landlord_user_id, house_id, house_publish_id, 1
FROM chat_conversation
WHERE id BETWEEN 941000000000000001 AND 941000000000000100;

INSERT IGNORE INTO chat_user_conversation
    (user_id, conversation_id, role_type, other_user_id, house_id, house_publish_id, status)
SELECT landlord_user_id, id, 2, tenant_user_id, house_id, house_publish_id, 1
FROM chat_conversation
WHERE id BETWEEN 941000000000000001 AND 941000000000000100;

COMMIT;

SELECT COUNT(*) AS fixture_conversations
FROM chat_conversation
WHERE id BETWEEN 941000000000000001 AND 941000000000000100
  AND house_publish_id = 900000000000000001;
