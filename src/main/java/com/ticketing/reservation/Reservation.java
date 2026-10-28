package com.ticketing.reservation;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        uniqueConstraints = @UniqueConstraint(columnNames = {"member_id", "schedule_id"}),
        // 회차별 확정 예매 수/판매량 집계(where schedule_id = ? and status = ?)용. unique 인덱스는 member_id 가 앞이라 쓸 수 없다.
        indexes = @Index(name = "idx_reservation_schedule_status", columnList = "schedule_id, status"))
public class Reservation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Column(name = "schedule_id", nullable = false)
    private Long scheduleId;

    @Column(nullable = false)
    private int quantity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ReservationStatus status;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public Reservation(Long memberId, Long scheduleId, int quantity) {
        this.memberId = memberId;
        this.scheduleId = scheduleId;
        this.quantity = quantity;
        this.status = ReservationStatus.CONFIRMED;
    }

    public void cancel() {
        if (status == ReservationStatus.CANCELED) {
            throw new IllegalStateException("이미 취소된 예매입니다.");
        }
        this.status = ReservationStatus.CANCELED;
    }
}
