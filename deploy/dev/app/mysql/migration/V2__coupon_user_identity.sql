-- Run once when coupon_template and user_coupon already exist.
-- Existing coupons default to tenant identity. Review existing data before production execution.

ALTER TABLE `coupon_template`
    ADD COLUMN `applicable_user_type` tinyint NOT NULL DEFAULT 0
        COMMENT '适用身份：0全部，1租客，2房东' AFTER `type`;

ALTER TABLE `user_coupon`
    ADD COLUMN `user_type` tinyint NOT NULL DEFAULT 1
        COMMENT '使用身份：1租客，2房东' AFTER `user_id`,
    DROP INDEX `uk_user_coupon_template`,
    DROP INDEX `idx_user_coupon_user_status`,
    ADD UNIQUE KEY `uk_user_coupon_template` (`user_type`, `user_id`, `template_id`),
    ADD KEY `idx_user_coupon_user_status` (`user_type`, `user_id`, `status`);
