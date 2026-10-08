package com.anyujia.anyujiacouponapi.constants;

/**
 * 优惠券业务常量。
 */
public final class CouponConstants {

    public static final int TYPE_FIXED = 1;
    public static final int TYPE_THRESHOLD = 2;
    public static final int TYPE_DISCOUNT = 3;

    public static final int SCOPE_PLATFORM = 1;
    public static final int SCOPE_REGION = 2;
    public static final int SCOPE_NATIONAL_SUBSIDY = 3;

    public static final int GRANT_MODE_PUBLIC = 1;
    public static final int GRANT_MODE_WHITELIST = 2;

    public static final int TEMPLATE_DISABLED = 0;
    public static final int TEMPLATE_ENABLED = 1;
    public static final int TEMPLATE_TERMINATED = 2;

    public static final int USER_COUPON_UNUSED = 0;
    public static final int USER_COUPON_LOCKED = 1;
    public static final int USER_COUPON_USED = 2;
    public static final int USER_COUPON_EXPIRED = 3;

    public static final int APPLICABLE_USER_ALL = 0;
    public static final int USER_TYPE_TENANT = 1;
    public static final int USER_TYPE_LANDLORD = 2;

    public static final int GRANT_TASK_PENDING = 0;
    public static final int GRANT_TASK_PROCESSING = 1;
    public static final int GRANT_TASK_FINISHED = 2;
    public static final int GRANT_TASK_FAILED = 3;

    private CouponConstants() {
    }
}
