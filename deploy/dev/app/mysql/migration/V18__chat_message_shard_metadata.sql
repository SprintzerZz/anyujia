CREATE TABLE IF NOT EXISTS `chat_conversation_shard`
(
    `conversation_id` BIGINT NOT NULL,
    `virtual_bucket` INT NOT NULL,
    `physical_shard` INT NOT NULL,
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`conversation_id`),
    UNIQUE KEY `uk_chat_conversation_shard_bucket` (`virtual_bucket`, `conversation_id`),
    KEY `idx_chat_conversation_shard_physical` (`physical_shard`, `conversation_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Conversation to message storage shard mapping';
