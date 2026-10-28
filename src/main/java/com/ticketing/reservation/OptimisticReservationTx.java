package com.ticketing.reservation;

import com.ticketing.common.OptimisticConflictException;
import com.ticketing.performance.Schedule;
import com.ticketing.performance.ScheduleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/** 낙관적 락의 1회 시도. 재시도마다 새 트랜잭션이 열리도록 별도 빈으로 분리했다. */
@Component
@RequiredArgsConstructor
class OptimisticReservationTx {

    private final ScheduleRepository scheduleRepository;
    private final ReservationRepository reservationRepository;

    @Transactional
    Long tryReserve(Long memberId, Long scheduleId, int quantity) {
        Schedule schedule = scheduleRepository.findById(scheduleId)
                .orElseThrow(() -> new IllegalStateException("존재하지 않는 회차입니다."));
        if (!schedule.isOpen(LocalDateTime.now())) {
            throw new IllegalStateException("아직 티켓 오픈 전입니다.");
        }
        if (schedule.getRemainingStock() < quantity) {
            throw new com.ticketing.common.SoldOutException();
        }
        if (scheduleRepository.decreaseStockIfVersionMatches(scheduleId, quantity, schedule.getVersion()) == 0) {
            throw new OptimisticConflictException();
        }
        return reservationRepository.save(new Reservation(memberId, scheduleId, quantity)).getId();
    }
}
