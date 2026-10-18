ALTER TABLE `chat_message_outbox`
    ADD INDEX `idx_chat_outbox_sent_cleanup` (`status`, `updated_at`, `id`);
