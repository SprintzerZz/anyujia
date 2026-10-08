-- Platform service-fee coupon tables.
-- The business design was adapted from FlashDeal/onecoupon-main under the MIT license.

CREATE TABLE IF NOT EXISTS `coupon_template`
(
    `id`                  bigint         NOT NULL AUTO_INCREMENT COMMENT '模板ID',
    `name`                varchar(128)   NOT NULL COMMENT '优惠券名称',
    `type`                tinyint        NOT NULL COMMENT '类型：1立减，2满减，3折扣',
    `applicable_user_type` tinyint       NOT NULL DEFAULT 0 COMMENT '适用身份：0全部，1租客，2房东',
    `scope_type`          tinyint        NOT NULL DEFAULT 1 COMMENT '范围：1平台，2区域，3国补',
    `province_code`       varchar(16)             DEFAULT NULL COMMENT '省级行政区划编码',
    `city_code`           varchar(16)             DEFAULT NULL COMMENT '城市行政区划编码',
    `district_code`       varchar(16)             DEFAULT NULL COMMENT '区县行政区划编码',
    `policy_code`         varchar(64)             DEFAULT NULL COMMENT '补贴政策批次',
    `grant_mode`          tinyint        NOT NULL DEFAULT 1 COMMENT '发放方式：1公开领取，2名单发放',
    `receive_limit`       int            NOT NULL DEFAULT 1 COMMENT '单用户限领次数',
    `threshold_amount`    decimal(12, 2)          DEFAULT NULL COMMENT '满减门槛',
    `discount_amount`     decimal(12, 2)          DEFAULT NULL COMMENT '立减或满减金额',
    `discount_rate`       decimal(5, 4)           DEFAULT NULL COMMENT '折扣率，例如0.8000表示八折',
    `max_discount_amount` decimal(12, 2)          DEFAULT NULL COMMENT '折扣券最高优惠金额',
    `total_stock`         int            NOT NULL COMMENT '总库存',
    `available_stock`     int            NOT NULL COMMENT '可领取库存',
    `valid_start_time`    datetime       NOT NULL COMMENT '生效时间',
    `valid_end_time`      datetime       NOT NULL COMMENT '失效时间',
    `status`              tinyint        NOT NULL DEFAULT 1 COMMENT '状态：0停用，1启用，2终止',
    `created_at`          datetime       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`          datetime       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_coupon_template_status_time` (`status`, `valid_start_time`, `valid_end_time`),
    KEY `idx_coupon_template_region` (`scope_type`, `province_code`, `city_code`, `district_code`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='平台服务费优惠券模板';

CREATE TABLE IF NOT EXISTS `user_coupon`
(
    `id`                 bigint         NOT NULL AUTO_INCREMENT COMMENT '用户优惠券ID',
    `user_id`            bigint         NOT NULL COMMENT '统一账号ID，对应app_user.id',
    `user_type`          tinyint        NOT NULL COMMENT '使用身份：1租客，2房东',
    `template_id`        bigint         NOT NULL COMMENT '优惠券模板ID',
    `receive_request_id` varchar(36)              DEFAULT NULL COMMENT '异步领券请求ID',
    `order_id`           bigint                  DEFAULT NULL COMMENT '平台服务费订单ID',
    `service_fee_amount` decimal(12, 2)           DEFAULT NULL COMMENT '锁券时平台服务费',
    `discount_amount`    decimal(12, 2)           DEFAULT NULL COMMENT '锁券时优惠金额',
    `status`             tinyint        NOT NULL DEFAULT 0 COMMENT '状态：0未使用，1已锁定，2已核销，3已过期',
    `received_at`        datetime       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `locked_at`          datetime                DEFAULT NULL,
    `used_at`            datetime                DEFAULT NULL,
    `updated_at`         datetime       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_coupon_template` (`user_type`, `user_id`, `template_id`),
    UNIQUE KEY `uk_user_coupon_receive_request` (`receive_request_id`),
    UNIQUE KEY `uk_user_coupon_order` (`order_id`),
    KEY `idx_user_coupon_user_status` (`user_type`, `user_id`, `status`),
    KEY `idx_user_coupon_template` (`template_id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='用户平台服务费优惠券';

CREATE TABLE IF NOT EXISTS `coupon_grant_task`
(
    `id`            bigint       NOT NULL AUTO_INCREMENT COMMENT '批量发券任务ID',
    `template_id`   bigint       NOT NULL COMMENT '优惠券模板ID',
    `user_type`     tinyint      NOT NULL COMMENT '发券用户身份',
    `total_count`   int          NOT NULL DEFAULT 0 COMMENT '总用户数',
    `success_count` int          NOT NULL DEFAULT 0 COMMENT '成功数',
    `failure_count` int          NOT NULL DEFAULT 0 COMMENT '失败数',
    `status`        tinyint      NOT NULL DEFAULT 0 COMMENT '状态：0待处理，1处理中，2完成，3失败',
    `remark`        varchar(512)          DEFAULT NULL COMMENT '备注',
    `created_at`    datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`    datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_coupon_grant_task_status_time` (`status`, `updated_at`),
    KEY `idx_coupon_grant_task_template` (`template_id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='批量发券任务';

CREATE TABLE IF NOT EXISTS `coupon_grant_fail_record`
(
    `id`          bigint       NOT NULL AUTO_INCREMENT COMMENT '失败记录ID',
    `task_id`     bigint       NOT NULL COMMENT '批量发券任务ID',
    `template_id` bigint       NOT NULL COMMENT '优惠券模板ID',
    `user_id`     bigint       NOT NULL COMMENT '用户ID',
    `user_type`   tinyint      NOT NULL COMMENT '用户身份',
    `reason`      varchar(255) NOT NULL COMMENT '失败原因',
    `created_at`  datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_coupon_grant_fail_task` (`task_id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='批量发券失败记录';
