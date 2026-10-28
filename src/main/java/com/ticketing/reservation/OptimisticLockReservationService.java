package com.ticketing.reservation;

import com.ticketing.common.OptimisticConflictException;
import com.ticketing.performance.ScheduleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 3주차 2: 낙관적 락. 잠그지 않고 읽은 뒤, version 이 그대로일 때만 차감(CAS)한다.
 * 충돌하면 짧게 쉬었다가 재시도한다. 충돌이 적으면 빠르지만, 한 회차에 요청이 몰리면 재시도가 폭증한다.
 */
@Service("optimistic")
@RequiredArgsConstructor
public class OptimisticLockReservationService implements ReservationService {

    static final int MAX_RETRIES = 100;

    private final OptimisticReservationTx tx;
    private final ScheduleRepository scheduleRepository;
    private final ReservationRepository reservationRepository;

    private final AtomicLong retryCount = new AtomicLong();

    /** 지금까지의 누적 재시도 횟수 (충돌 비용 측정용). */
    public long retryCount() {
        return retryCount.get();
    }

    @Override
    public Long reserve(Long memberId, Long scheduleId, int quantity) {
        for (int attempt = 0; ; attempt++) {
            try {
                return tx.tryReserve(memberId, scheduleId, quantity);
            } catch (OptimisticConflictException e) {
                if (attempt >= MAX_RETRIES) {
                    throw e;
                }
                retryCount.incrementAndGet();
                sleepQuietly(ThreadLocalRandom.current().nextInt(1, 6));
            }
        }
    }

    @Override
    @Transactional
    public void cancel(Long memberId, Long reservationId) {
        Reservation reservation = reservationRepository.findById(reservationId)
                .filter(r -> r.getMemberId().equals(memberId))
                .orElseThrow(() -> new IllegalStateException("존재하지 않는 예매입니다."));
        reservation.cancel();
        scheduleRepository.increaseStock(reservation.getScheduleId(), reservation.getQuantity());
    }

    private static void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
