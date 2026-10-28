package com.ticketing.notification;

/** 예매 트랜잭션이 끝난 뒤 발행되는 이벤트. 알림 처리는 예매 응답 시간에 영향을 주지 않도록 비동기로 한다. */
public final class ReservationEvents {

    private ReservationEvents() {}

    public record Confirmed(Long memberId, Long reservationId, Long scheduleId, int quantity) {}

    public record Canceled(Long memberId, Long reservationId, Long scheduleId, int quantity) {}
}
