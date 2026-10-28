package com.ticketing.common;

/** 낙관적 락 충돌. 재시도 대상이며, 재시도 횟수를 초과하면 클라이언트에 409 로 전달된다. */
public class OptimisticConflictException extends RuntimeException {

    public OptimisticConflictException() {
        super("요청이 몰려 처리하지 못했습니다. 다시 시도해주세요.");
    }
}
