ALTER TABLE `house`
    ADD COLUMN `image_assets_json` json NULL COMMENT 'House images with OSS url/key' AFTER `cover_image_url`;

ALTER TABLE `house_publish`
    ADD COLUMN `audit_remark` varchar(500) NULL COMMENT 'Audit remark' AFTER `audit_status`,
    ADD COLUMN `audited_by` bigint NULL COMMENT 'Auditor user ID' AFTER `audit_remark`,
    ADD COLUMN `audited_at` datetime NULL COMMENT 'Audit time' AFTER `audited_by`;
