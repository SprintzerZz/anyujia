# 安寓家平台服务费优惠券

本模块从 FlashDeal/onecoupon-main 的优惠券领域设计中提取核心流程，并适配为安寓家平台服务费优惠券。
原项目采用 MIT License，许可证文本见当前目录下的 `LICENSE`。

## 模块

- `anyujia-coupon-api`：DTO、VO 和 Feign 接口。
- `anyujia-coupon-service`：模板、领券、优惠计算和状态流转。

## 业务约束

- 优惠券只能抵扣平台服务费，不能抵扣租金或押金。
- 每位 C 端用户对同一个模板限领一张。
- 模板管理接口要求请求来源 `userFrom=sys`。
- 用户领券、查券和锁券接口要求请求来源 `userFrom=app`。
- 核销、释放和退款接口仅允许订单服务或后台调用。

## 订单调用顺序

1. 创建服务费订单前调用 `/coupon/available` 查询可用券。
2. 创建待支付订单时调用 `/coupon/lock`，保存返回的实付金额。
3. 支付成功后调用 `/coupon/consume`。
4. 订单取消或支付超时后调用 `/coupon/release`。
5. 支付后退款成功时调用 `/coupon/refund`。

重复领取由 Redis 分布式锁拦截，数据库原子更新保证库存不会减为负数，唯一索引作为最终一致性保护。

## 启动

1. 执行 `deploy/dev/app/mysql/sql/coupon.sql`。
2. 确认 Nacos 中存在 `anyujia-coupon-dev.yaml`，或者通过环境变量覆盖数据库配置。
3. 启动 `AnyujiaCouponApplication`，默认端口为 `18084`。
4. 网关通过 `/coupon/**` 转发到 `anyujia-coupon`。

C 端用户登录签发的 `LoginUserDTO.userFrom` 必须为 `app`，否则用户优惠券接口会拒绝访问。
