package com.ticketing.reservation;

/** 재고 차감 방식(락 없음, 비관적 락 ...)별로 구현을 나눠 같은 시나리오로 비교한다. */
public interface ReservationService {

    Long reserve(Long memberId, Long scheduleId, int quantity);

    void cancel(Long memberId, Long reservationId);
}
