CREATE TABLE IF NOT EXISTS `chat_conversation`
(
    `id`                bigint      NOT NULL,
    `tenant_user_id`    bigint      NOT NULL,
    `landlord_user_id`  bigint      NOT NULL,
    `house_id`          bigint      NOT NULL,
    `house_publish_id`  bigint      NOT NULL,
    `last_message_id`   bigint               DEFAULT NULL,
    `last_message_seq`  bigint               DEFAULT NULL,
    `next_message_seq`  bigint      NOT NULL DEFAULT 0,
    `last_message_time` bigint               DEFAULT NULL,
    `status`            tinyint     NOT NULL DEFAULT 1,
    `created_at`        datetime    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`        datetime    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_chat_conversation_listing` (`tenant_user_id`, `landlord_user_id`, `house_publish_id`),
    KEY `idx_chat_conversation_tenant_time` (`tenant_user_id`, `last_message_time`, `id`),
    KEY `idx_chat_conversation_landlord_time` (`landlord_user_id`, `last_message_time`, `id`),
    KEY `idx_chat_conversation_publish` (`house_publish_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Tenant-landlord listing conversation';

CREATE TABLE IF NOT EXISTS `chat_conversation_member`
(
    `id`                   bigint   NOT NULL AUTO_INCREMENT,
    `conversation_id`      bigint   NOT NULL,
    `user_id`              bigint   NOT NULL,
    `role_type`            tinyint  NOT NULL COMMENT '1 tenant, 2 landlord',
    `unread_count`         int      NOT NULL DEFAULT 0,
    `last_read_message_id` bigint            DEFAULT NULL,
    `last_read_conversation_seq` bigint       DEFAULT NULL,
    `created_at`           datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`           datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_chat_member` (`conversation_id`, `user_id`),
    KEY `idx_chat_member_user` (`user_id`, `conversation_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Conversation member state';

CREATE TABLE IF NOT EXISTS `chat_message`
(
    `id`                bigint        NOT NULL,
    `client_message_id` varchar(64)   NOT NULL,
    `conversation_id`   bigint        NOT NULL,
    `conversation_seq`  bigint        NOT NULL,
    `sender_id`         bigint        NOT NULL,
    `recipient_id`      bigint        NOT NULL,
    `type`              tinyint       NOT NULL,
    `content`           varchar(1024) NOT NULL DEFAULT '',
    `status`            tinyint       NOT NULL DEFAULT 0,
    `visited`           tinyint       NOT NULL DEFAULT 10,
    `created_at`        datetime(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_chat_message_client` (`sender_id`, `client_message_id`),
    UNIQUE KEY `uk_chat_message_conversation_seq` (`conversation_id`, `conversation_seq`),
    KEY `idx_chat_message_page` (`conversation_id`, `id`),
    KEY `idx_chat_message_conversation_page` (`conversation_id`, `conversation_seq`),
    KEY `idx_chat_message_recipient_visited` (`conversation_id`, `recipient_id`, `visited`, `id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Chat message';

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
