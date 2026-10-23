CREATE TABLE IF NOT EXISTS `chat_outbox_reconcile_cursor`
(
    `source_key`      VARCHAR(64) NOT NULL,
    `last_message_id` BIGINT      NOT NULL DEFAULT 0,
    `updated_at`      DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`source_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Durable cursors for chat message to outbox reconciliation';
