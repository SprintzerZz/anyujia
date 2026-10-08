CREATE TABLE IF NOT EXISTS `chat_user_conversation`
(
    `user_id`                    bigint      NOT NULL,
    `conversation_id`            bigint      NOT NULL,
    `role_type`                  tinyint     NOT NULL COMMENT '1 tenant, 2 landlord',
    `other_user_id`              bigint      NOT NULL,
    `house_id`                   bigint      NOT NULL,
    `house_publish_id`           bigint      NOT NULL,
    `last_message_id`            bigint               DEFAULT NULL,
    `last_message_seq`           bigint               DEFAULT NULL,
    `last_message_time`          bigint               DEFAULT NULL,
    `unread_count`               int         NOT NULL DEFAULT 0,
    `last_read_message_id`       bigint               DEFAULT NULL,
    `last_read_conversation_seq` bigint               DEFAULT NULL,
    `status`                     tinyint     NOT NULL DEFAULT 1,
    `created_at`                 datetime    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`                 datetime    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`user_id`, `conversation_id`),
    KEY `idx_chat_user_conversation_page`
        (`user_id`, `status`, `last_message_time`, `conversation_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='User-routed conversation index';

INSERT IGNORE INTO `chat_user_conversation`
    (`user_id`, `conversation_id`, `role_type`, `other_user_id`, `house_id`, `house_publish_id`,
     `last_message_id`, `last_message_seq`, `last_message_time`, `unread_count`,
     `last_read_message_id`, `last_read_conversation_seq`, `status`, `created_at`, `updated_at`)
SELECT member.user_id,
       conversation.id,
       member.role_type,
       CASE
           WHEN member.user_id = conversation.tenant_user_id THEN conversation.landlord_user_id
           ELSE conversation.tenant_user_id
       END,
       conversation.house_id,
       conversation.house_publish_id,
       conversation.last_message_id,
       conversation.last_message_seq,
       conversation.last_message_time,
       member.unread_count,
       member.last_read_message_id,
       member.last_read_conversation_seq,
       conversation.status,
       conversation.created_at,
       GREATEST(conversation.updated_at, member.updated_at)
  FROM chat_conversation AS conversation
  JOIN chat_conversation_member AS member
    ON member.conversation_id = conversation.id;
