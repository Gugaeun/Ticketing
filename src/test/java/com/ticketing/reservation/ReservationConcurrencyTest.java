package com.ticketing.reservation;

import com.ticketing.member.Member;
import com.ticketing.member.MemberRepository;
import com.ticketing.performance.Performance;
import com.ticketing.performance.PerformanceRepository;
import com.ticketing.performance.Schedule;
import com.ticketing.performance.ScheduleRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 같은 시나리오(재고 100, 동시 요청 1,000)로 재고 차감 방식을 비교한다.
 * 기본 DB 는 H2 이므로 수치는 참고용이며, 최종 측정은 MySQL(--spring.profiles.active=mysql)에서 한다.
 */
@SpringBootTest
class ReservationConcurrencyTest {

    @Autowired NoLockReservationService noLock;
    @Autowired PessimisticLockReservationService pessimistic;
    @Autowired OptimisticLockReservationService optimistic;
    @Autowired ReservationRepository reservationRepository;
    @Autowired ScheduleRepository scheduleRepository;
    @Autowired PerformanceRepository performanceRepository;
    @Autowired MemberRepository memberRepository;

    static final int STOCK = 100;
    static final int REQUESTS = 1000;

    record Result(int success, long confirmed, int remaining, long elapsedMillis) {}

    /** 문제 재현: 결과만 기록한다. (실패시키지 않는다) */
    @Test
    void 락_없음_초과_판매가_발생한다() throws InterruptedException {
        Result r = run("none", noLock);
        System.out.printf("[동시성 결과][락 없음] 재고=%d, 요청=%d, 성공 응답=%d, 확정 예매=%d, 남은 재고=%d, 초과 판매=%d건, %dms%n",
                STOCK, REQUESTS, r.success(), r.confirmed(), r.remaining(),
                Math.max(0, r.confirmed() - STOCK), r.elapsedMillis());
    }

    /** 해결 검증: 정확히 재고만큼만 팔려야 한다. */
    @Test
    void 비관적_락_재고만큼만_판매된다() throws InterruptedException {
        Result r = run("pessimistic", pessimistic);
        System.out.printf("[동시성 결과][비관적 락] 재고=%d, 요청=%d, 성공 응답=%d, 확정 예매=%d, 남은 재고=%d, 초과 판매=%d건, %dms%n",
                STOCK, REQUESTS, r.success(), r.confirmed(), r.remaining(),
                Math.max(0, r.confirmed() - STOCK), r.elapsedMillis());

        assertThat(r.confirmed()).isEqualTo(STOCK);
        assertThat(r.success()).isEqualTo(STOCK);
        assertThat(r.remaining()).isZero();
    }

    /** 해결 검증: 충돌 시 재시도하므로 정확히 재고만큼 판매되어야 한다. 재시도 횟수는 충돌 비용 지표. */
    @Test
    void 낙관적_락_재고만큼만_판매된다() throws InterruptedException {
        long retriesBefore = optimistic.retryCount();
        Result r = run("optimistic", optimistic);
        System.out.printf("[동시성 결과][낙관적 락] 재고=%d, 요청=%d, 성공 응답=%d, 확정 예매=%d, 남은 재고=%d, 초과 판매=%d건, 재시도=%d회, %dms%n",
                STOCK, REQUESTS, r.success(), r.confirmed(), r.remaining(),
                Math.max(0, r.confirmed() - STOCK), optimistic.retryCount() - retriesBefore, r.elapsedMillis());

        assertThat(r.confirmed()).isEqualTo(STOCK);
        assertThat(r.success()).isEqualTo(STOCK);
        assertThat(r.remaining()).isZero();
    }

    private Result run(String label, ReservationService service) throws InterruptedException {
        Performance performance = performanceRepository.save(new Performance("동시성-" + label, "올림픽홀", "설명"));
        Schedule schedule = scheduleRepository.save(new Schedule(
                performance, LocalDateTime.now().plusDays(7), LocalDateTime.now().minusMinutes(1), STOCK));

        List<Long> memberIds = new ArrayList<>();
        for (int i = 0; i < REQUESTS; i++) {
            memberIds.add(memberRepository.save(
                    new Member("concurrency-" + label + "-" + i + "@test.com", "pw", "user" + i)).getId());
        }

        ExecutorService pool = Executors.newFixedThreadPool(64);
        CountDownLatch done = new CountDownLatch(REQUESTS);
        AtomicInteger success = new AtomicInteger();

        long begin = System.currentTimeMillis();
        for (Long memberId : memberIds) {
            pool.submit(() -> {
                try {
                    service.reserve(memberId, schedule.getId(), 1);
                    success.incrementAndGet();
                } catch (Exception ignored) {
                    // 재고 부족 등 실패 응답은 정상 케이스
                } finally {
                    done.countDown();
                }
            });
        }
        done.await();
        long elapsed = System.currentTimeMillis() - begin;
        pool.shutdown();

        long confirmed = reservationRepository.countByScheduleIdAndStatus(schedule.getId(), ReservationStatus.CONFIRMED);
        int remaining = scheduleRepository.findById(schedule.getId()).orElseThrow().getRemainingStock();
        return new Result(success.get(), confirmed, remaining, elapsed);
    }
}
