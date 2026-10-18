CREATE TABLE IF NOT EXISTS `chat_message_outbox`
(
    `id` BIGINT NOT NULL,
    `message_id` BIGINT NOT NULL,
    `recipient_id` BIGINT NOT NULL,
    `event_type` VARCHAR(32) NOT NULL,
    `payload` JSON NOT NULL,
    `status` TINYINT NOT NULL DEFAULT 0 COMMENT '0 pending, 1 processing, 2 sent',
    `retry_count` INT NOT NULL DEFAULT 0,
    `next_retry_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_chat_outbox_event` (`message_id`, `event_type`),
    KEY `idx_chat_outbox_pending` (`status`, `next_retry_at`, `id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Reliable post-commit chat events';
