ALTER TABLE `chat_message_outbox`
    DROP INDEX `uk_chat_outbox_event`,
    ADD UNIQUE KEY `uk_chat_outbox_event` (`message_id`, `event_type`, `recipient_id`);
