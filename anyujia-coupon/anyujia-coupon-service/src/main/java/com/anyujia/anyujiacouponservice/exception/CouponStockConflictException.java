package com.anyujia.anyujiacouponservice.exception;

public class CouponStockConflictException extends RuntimeException {

    public CouponStockConflictException(String message) {
        super(message);
    }
}
