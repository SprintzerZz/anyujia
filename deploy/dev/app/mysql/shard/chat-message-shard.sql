CREATE DATABASE IF NOT EXISTS frameworkjava_dev
  CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE frameworkjava_dev;

CREATE TABLE IF NOT EXISTS chat_message_template (
  id bigint NOT NULL,
  client_message_id varchar(64) NOT NULL,
  conversation_id bigint NOT NULL,
  conversation_seq bigint NOT NULL,
  sender_id bigint NOT NULL,
  recipient_id bigint NOT NULL,
  type tinyint NOT NULL,
  content varchar(1024) NOT NULL DEFAULT '',
  status tinyint NOT NULL DEFAULT 0,
  visited tinyint NOT NULL DEFAULT 10,
  created_at datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_chat_message_client (sender_id, client_message_id),
  UNIQUE KEY uk_chat_message_conversation_seq (conversation_id, conversation_seq),
  KEY idx_chat_message_page (conversation_id, id),
  KEY idx_chat_message_conversation_page (conversation_id, conversation_seq),
  KEY idx_chat_message_recipient_visited (conversation_id, recipient_id, visited, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

DELIMITER $$
CREATE PROCEDURE create_chat_message_shards(IN first_shard INT, IN last_shard INT)
BEGIN
  DECLARE shard_no INT DEFAULT first_shard;
  WHILE shard_no <= last_shard DO
    SET @table_name = CONCAT('chat_message_s', LPAD(shard_no, 2, '0'));
    SET @statement = CONCAT('CREATE TABLE IF NOT EXISTS `', @table_name,
                            '` LIKE chat_message_template');
    PREPARE create_statement FROM @statement;
    EXECUTE create_statement;
    DEALLOCATE PREPARE create_statement;
    SET shard_no = shard_no + 1;
  END WHILE;
END$$
DELIMITER ;
