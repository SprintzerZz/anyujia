CREATE TABLE IF NOT EXISTS chat_image_asset (
    asset_id varchar(36) NOT NULL,
    conversation_id bigint NOT NULL,
    owner_id bigint NOT NULL,
    object_key varchar(512) NOT NULL,
    url varchar(1024) NOT NULL,
    content_type varchar(32) NOT NULL,
    size_bytes bigint NOT NULL,
    width int NOT NULL,
    height int NOT NULL,
    created_at datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (asset_id),
    KEY idx_chat_image_asset_owner (owner_id, conversation_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
