-- Distinguishes RocketMQ redelivery from a different duplicate receive request.

ALTER TABLE `user_coupon`
    ADD COLUMN `receive_request_id` varchar(80) DEFAULT NULL
        COMMENT '异步领券请求ID' AFTER `template_id`,
    ADD UNIQUE KEY `uk_user_coupon_receive_request` (`receive_request_id`);
