ALTER TABLE chat_image_asset
    ADD COLUMN upload_id varchar(36) NULL AFTER owner_id,
    ADD UNIQUE KEY uk_chat_image_upload (owner_id, conversation_id, upload_id);
