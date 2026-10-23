ALTER TABLE `chat_message_outbox`
    ADD COLUMN `claim_token` VARCHAR(36) NULL AFTER `retry_count`,
    ADD INDEX `idx_chat_outbox_claim` (`claim_token`, `id`);
