package com.ticketing.performance;

import com.ticketing.common.SoldOutException;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Schedule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "performance_id")
    private Performance performance;

    @Column(nullable = false)
    private LocalDateTime startAt;

    @Column(nullable = false)
    private LocalDateTime ticketOpenAt;

    @Column(nullable = false)
    private int totalStock;

    @Column(nullable = false)
    private int remainingStock;

    // 낙관적 락 비교용 컬럼. JPA @Version 을 쓰면 "락 없음" 비교 구현도 보호되어 초과 판매를 재현할 수 없으므로,
    // 낙관적 락 서비스에서만 version 조건을 건 UPDATE 로 사용한다.
    private long version;

    public Schedule(Performance performance, LocalDateTime startAt, LocalDateTime ticketOpenAt, int totalStock) {
        this.performance = performance;
        this.startAt = startAt;
        this.ticketOpenAt = ticketOpenAt;
        this.totalStock = totalStock;
        this.remainingStock = totalStock;
    }

    public void changeTicketOpenAt(LocalDateTime ticketOpenAt) {
        this.ticketOpenAt = ticketOpenAt;
    }

    public boolean isOpen(LocalDateTime now) {
        return !now.isBefore(ticketOpenAt);
    }

    public void decrease(int quantity) {
        if (remainingStock < quantity) {
            throw new SoldOutException();
        }
        this.remainingStock -= quantity;
    }

    public void increase(int quantity) {
        this.remainingStock += quantity;
    }
}
