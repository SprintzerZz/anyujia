-- Add a conversation-local cursor while retaining Snowflake message IDs as global identifiers.
ALTER TABLE `chat_conversation`
    ADD COLUMN `last_message_seq` bigint DEFAULT NULL AFTER `last_message_id`,
    ADD COLUMN `next_message_seq` bigint NOT NULL DEFAULT 0 AFTER `last_message_seq`;

ALTER TABLE `chat_conversation_member`
    ADD COLUMN `last_read_conversation_seq` bigint DEFAULT NULL AFTER `last_read_message_id`;

ALTER TABLE `chat_message`
    ADD COLUMN `conversation_seq` bigint DEFAULT NULL AFTER `conversation_id`;

UPDATE `chat_message` AS message
JOIN (
    SELECT ranked_source.id, ranked_source.conversation_seq
      FROM (
          SELECT id,
                 ROW_NUMBER() OVER (PARTITION BY conversation_id ORDER BY id) AS conversation_seq
            FROM `chat_message`
      ) AS ranked_source
) AS ranked ON ranked.id = message.id
SET message.conversation_seq = ranked.conversation_seq;

UPDATE `chat_conversation` AS conversation
LEFT JOIN (
    SELECT conversation_id,
           MAX(conversation_seq) AS max_sequence
      FROM `chat_message`
     GROUP BY conversation_id
) AS message_sequence ON message_sequence.conversation_id = conversation.id
SET conversation.next_message_seq = COALESCE(message_sequence.max_sequence, 0),
    conversation.last_message_seq = message_sequence.max_sequence;

UPDATE `chat_conversation_member` AS member
LEFT JOIN `chat_message` AS message ON message.id = member.last_read_message_id
SET member.last_read_conversation_seq = message.conversation_seq;

ALTER TABLE `chat_message`
    MODIFY COLUMN `conversation_seq` bigint NOT NULL,
    ADD UNIQUE KEY `uk_chat_message_conversation_seq` (`conversation_id`, `conversation_seq`),
    ADD KEY `idx_chat_message_conversation_page` (`conversation_id`, `conversation_seq`);
