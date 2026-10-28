package com.ticketing.reservation;

import com.ticketing.performance.Schedule;
import com.ticketing.performance.ScheduleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 3주차 1: 비관적 락. 회차 행을 SELECT ... FOR UPDATE 로 잠근 뒤 재고를 차감한다.
 * 같은 회차에 대한 요청은 직렬화되므로 정합성은 보장되지만, 인기 회차에서는 대기 시간이 길어진다.
 */
@Service("pessimistic")
@RequiredArgsConstructor
public class PessimisticLockReservationService implements ReservationService {

    private final ScheduleRepository scheduleRepository;
    private final ReservationRepository reservationRepository;

    @Override
    @Transactional
    public Long reserve(Long memberId, Long scheduleId, int quantity) {
        Schedule schedule = scheduleRepository.findByIdForUpdate(scheduleId)
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
        scheduleRepository.findByIdForUpdate(reservation.getScheduleId())
                .orElseThrow()
                .increase(reservation.getQuantity());
    }
}
