-- Physical message tables. Keep the feature flag disabled until historical
-- rows have been backfilled and verified in these tables.
CREATE TABLE IF NOT EXISTS `chat_message_s00` LIKE `chat_message`;
CREATE TABLE IF NOT EXISTS `chat_message_s01` LIKE `chat_message`;
CREATE TABLE IF NOT EXISTS `chat_message_s02` LIKE `chat_message`;
CREATE TABLE IF NOT EXISTS `chat_message_s03` LIKE `chat_message`;
CREATE TABLE IF NOT EXISTS `chat_message_s04` LIKE `chat_message`;
CREATE TABLE IF NOT EXISTS `chat_message_s05` LIKE `chat_message`;
CREATE TABLE IF NOT EXISTS `chat_message_s06` LIKE `chat_message`;
CREATE TABLE IF NOT EXISTS `chat_message_s07` LIKE `chat_message`;
CREATE TABLE IF NOT EXISTS `chat_message_s08` LIKE `chat_message`;
CREATE TABLE IF NOT EXISTS `chat_message_s09` LIKE `chat_message`;
CREATE TABLE IF NOT EXISTS `chat_message_s10` LIKE `chat_message`;
CREATE TABLE IF NOT EXISTS `chat_message_s11` LIKE `chat_message`;
CREATE TABLE IF NOT EXISTS `chat_message_s12` LIKE `chat_message`;
CREATE TABLE IF NOT EXISTS `chat_message_s13` LIKE `chat_message`;
CREATE TABLE IF NOT EXISTS `chat_message_s14` LIKE `chat_message`;
CREATE TABLE IF NOT EXISTS `chat_message_s15` LIKE `chat_message`;
CREATE TABLE IF NOT EXISTS `chat_message_s16` LIKE `chat_message`;
CREATE TABLE IF NOT EXISTS `chat_message_s17` LIKE `chat_message`;
CREATE TABLE IF NOT EXISTS `chat_message_s18` LIKE `chat_message`;
CREATE TABLE IF NOT EXISTS `chat_message_s19` LIKE `chat_message`;
CREATE TABLE IF NOT EXISTS `chat_message_s20` LIKE `chat_message`;
CREATE TABLE IF NOT EXISTS `chat_message_s21` LIKE `chat_message`;
CREATE TABLE IF NOT EXISTS `chat_message_s22` LIKE `chat_message`;
CREATE TABLE IF NOT EXISTS `chat_message_s23` LIKE `chat_message`;
CREATE TABLE IF NOT EXISTS `chat_message_s24` LIKE `chat_message`;
CREATE TABLE IF NOT EXISTS `chat_message_s25` LIKE `chat_message`;
CREATE TABLE IF NOT EXISTS `chat_message_s26` LIKE `chat_message`;
CREATE TABLE IF NOT EXISTS `chat_message_s27` LIKE `chat_message`;
CREATE TABLE IF NOT EXISTS `chat_message_s28` LIKE `chat_message`;
CREATE TABLE IF NOT EXISTS `chat_message_s29` LIKE `chat_message`;
CREATE TABLE IF NOT EXISTS `chat_message_s30` LIKE `chat_message`;
CREATE TABLE IF NOT EXISTS `chat_message_s31` LIKE `chat_message`;

-- Seed deterministic mappings for existing conversations.
INSERT IGNORE INTO `chat_conversation_shard`
    (`conversation_id`, `virtual_bucket`, `physical_shard`)
SELECT id,
       MOD(id, 1024),
       MOD(MOD(id, 1024), 32)
  FROM `chat_conversation`;
