package com.dianpingplus.utils;

/**
 * 业务异常，用于可预知的业务错误（库存不足、重复下单等）
 */
public class BusinessException extends RuntimeException {
    public BusinessException(String message) {
        super(message);
    }
}
