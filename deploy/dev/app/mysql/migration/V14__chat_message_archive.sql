CREATE TABLE IF NOT EXISTS `chat_message_archive`
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
    UNIQUE KEY `uk_chat_archive_client` (`sender_id`, `client_message_id`),
    UNIQUE KEY `uk_chat_archive_conversation_seq` (`conversation_id`, `conversation_seq`),
    KEY `idx_chat_archive_page` (`conversation_id`, `id`),
    KEY `idx_chat_archive_conversation_page` (`conversation_id`, `conversation_seq`),
    KEY `idx_chat_archive_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Archived chat message';
