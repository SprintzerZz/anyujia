CREATE TABLE IF NOT EXISTS `chat_message_shard_backfill_progress`
(
    `id` BIGINT NOT NULL,
    `last_message_id` BIGINT NOT NULL DEFAULT 0,
    `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Restartable chat message shard backfill cursor';

INSERT IGNORE INTO `chat_message_shard_backfill_progress` (`id`, `last_message_id`)
VALUES (1, 0);
