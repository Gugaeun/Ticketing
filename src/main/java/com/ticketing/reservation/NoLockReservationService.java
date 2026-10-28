package com.ticketing.reservation;

import com.ticketing.performance.Schedule;
import com.ticketing.performance.ScheduleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 1단계: 락 없는 예매. 동시 요청 시 초과 판매가 발생하는 것을 재현하기 위한 의도적인 구현.
 * 운영 경로(reservation.strategy)로는 사용하지 않고 비교 테스트에서만 쓴다.
 */
@Service("none")
@RequiredArgsConstructor
public class NoLockReservationService implements ReservationService {

    private final ScheduleRepository scheduleRepository;
    private final ReservationRepository reservationRepository;

    @Override
    @Transactional
    public Long reserve(Long memberId, Long scheduleId, int quantity) {
        Schedule schedule = scheduleRepository.findById(scheduleId)
                .orElseThrow(() -> new IllegalStateException("존재하지 않는 회차입니다."));
        if (!schedule.isOpen(LocalDateTime.now())) {
            throw new IllegalStateException("아직 티켓 오픈 전입니다.");
        }
        schedule.decrease(quantity);
        return reservationRepository.save(new Reservation(memberId, scheduleId, quantity)).getId();
    }

    @Override
    @Transactional
    public void cancel(Long memberId, Long reservationId) {
        Reservation reservation = reservationRepository.findById(reservationId)
                .filter(r -> r.getMemberId().equals(memberId))
                .orElseThrow(() -> new IllegalStateException("존재하지 않는 예매입니다."));
        reservation.cancel();
        scheduleRepository.findById(reservation.getScheduleId())
                .orElseThrow()
                .increase(reservation.getQuantity());
    }
}
