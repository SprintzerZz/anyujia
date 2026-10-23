-- Isolated, idempotent load-test fixtures. Reserved IDs: 910000000000000000..031.
-- Users 1 and 2 are the standard dev chat identities seeded by V1__chat_identity_seed.sql.
INSERT IGNORE INTO chat_conversation
    (id, tenant_user_id, landlord_user_id, house_id, house_publish_id,
     next_message_seq, status, created_at, updated_at)
SELECT 910000000000000000 + n, 1, 2, 920000000000000000 + n, 930000000000000000 + n,
       0, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM (
    SELECT ones.n + tens.n * 10 AS n
    FROM (SELECT 0 n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4
          UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) ones
    CROSS JOIN (SELECT 0 n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3) tens
) numbers
WHERE n < 32;

INSERT IGNORE INTO chat_conversation_member
    (conversation_id, user_id, role_type, unread_count, created_at, updated_at)
SELECT conversation.id, users.user_id, users.role_type, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM chat_conversation conversation
CROSS JOIN (
    SELECT 1 AS user_id, 1 AS role_type
    UNION ALL SELECT 2 AS user_id, 2 AS role_type
) users
WHERE conversation.id BETWEEN 910000000000000000 AND 910000000000000031;

INSERT IGNORE INTO chat_user_conversation
    (user_id, conversation_id, role_type, other_user_id, house_id, house_publish_id,
     unread_count, status, created_at, updated_at)
SELECT users.user_id, conversation.id, users.role_type, users.other_user_id,
       conversation.house_id, conversation.house_publish_id, 0, 1,
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM chat_conversation conversation
CROSS JOIN (
    SELECT 1 AS user_id, 1 AS role_type, 2 AS other_user_id
    UNION ALL SELECT 2 AS user_id, 2 AS role_type, 1 AS other_user_id
) users
WHERE conversation.id BETWEEN 910000000000000000 AND 910000000000000031;
