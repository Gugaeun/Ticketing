package com.ticketing.common;

public class SoldOutException extends RuntimeException {

    public SoldOutException() {
        super("재고가 부족합니다.");
    }
}
