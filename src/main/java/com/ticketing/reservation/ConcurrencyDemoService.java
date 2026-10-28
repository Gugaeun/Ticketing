package com.ticketing.reservation;

import com.ticketing.member.Member;
import com.ticketing.member.MemberRepository;
import com.ticketing.performance.Performance;
import com.ticketing.performance.PerformanceRepository;
import com.ticketing.performance.Schedule;
import com.ticketing.performance.ScheduleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 관리자 시연용: 임시 공연/회차를 만들어 재고 N장에 M명이 동시에 예매하게 하고 결과를 돌려준다.
 * 시연 회원(demo{i}@demo.local)과 임시 공연/회차/예매는 실행 후 모두 지운다.
 * 서비스를 직접 호출하므로 예매 완료 알림은 발생하지 않는다.
 */
@Service
@RequiredArgsConstructor
public class ConcurrencyDemoService {

    private static final int THREADS = 64;

    public record Result(String strategy, int stock, int requests, int success, long confirmed,
                         int remaining, long oversold, long retries, long elapsedMillis) {}

    private final Map<String, ReservationService> strategies;
    private final ReservationRepository reservationRepository;
    private final ScheduleRepository scheduleRepository;
    private final PerformanceRepository performanceRepository;
    private final MemberRepository memberRepository;

    private final AtomicBoolean running = new AtomicBoolean();

    public Result run(String strategy, int stock, int requests) {
        ReservationService service = strategies.get(strategy);
        if (service == null) {
            throw new IllegalStateException("알 수 없는 방식입니다: " + strategy);
        }
        stock = Math.max(1, Math.min(stock, 1000));
        requests = Math.max(1, Math.min(requests, 2000));
        if (!running.compareAndSet(false, true)) {
            throw new IllegalStateException("이미 시연이 실행 중입니다. 잠시 후 다시 시도해주세요.");
        }

        Performance performance = null;
        Schedule schedule = null;
        List<Long> memberIds = List.of();
        try {
            performance = performanceRepository.save(new Performance("[시연] 동시성 테스트", "임시", "시연 후 삭제됩니다."));
            schedule = scheduleRepository.save(new Schedule(
                    performance, LocalDateTime.now().plusDays(7), LocalDateTime.now().minusMinutes(1), stock));
            memberIds = demoMemberIds(requests);

            long retriesBefore = service instanceof OptimisticLockReservationService o ? o.retryCount() : 0;
            ExecutorService pool = Executors.newFixedThreadPool(THREADS);
            CountDownLatch done = new CountDownLatch(requests);
            AtomicInteger success = new AtomicInteger();
            Long scheduleId = schedule.getId();

            long begin = System.currentTimeMillis();
            for (Long memberId : memberIds) {
                pool.submit(() -> {
                    try {
                        service.reserve(memberId, scheduleId, 1);
                        success.incrementAndGet();
                    } catch (Exception ignored) {
                        // 재고 부족/충돌 한도 초과 등은 실패 응답으로 센다.
                    } finally {
                        done.countDown();
                    }
                });
            }
            done.await();
            long elapsed = System.currentTimeMillis() - begin;
            pool.shutdown();

            long confirmed = reservationRepository.countByScheduleIdAndStatus(scheduleId, ReservationStatus.CONFIRMED);
            int remaining = scheduleRepository.findById(scheduleId).orElseThrow().getRemainingStock();
            long retries = service instanceof OptimisticLockReservationService o ? o.retryCount() - retriesBefore : 0;
            return new Result(strategy, stock, requests, success.get(), confirmed, remaining,
                    Math.max(0, confirmed - stock), retries, elapsed);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("시연이 중단되었습니다.");
        } finally {
            try {
                if (schedule != null) {
                    reservationRepository.deleteByScheduleId(schedule.getId());
                    scheduleRepository.deleteById(schedule.getId());
                }
                if (performance != null) {
                    performanceRepository.deleteById(performance.getId());
                }
                // 시연용 회원도 지워서 회원 테이블에 demo 행이 쌓이지 않게 한다. (IN 절 개수 제한을 피해 나눠서 삭제)
                for (int i = 0; i < memberIds.size(); i += 500) {
                    memberRepository.deleteAllByIdInBatch(memberIds.subList(i, Math.min(i + 500, memberIds.size())));
                }
            } finally {
                running.set(false);
            }
        }
    }

    private List<Long> demoMemberIds(int count) {
        List<String> emails = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            emails.add("demo" + i + "@demo.local");
        }
        Map<String, Long> existing = new HashMap<>();
        memberRepository.findByEmailIn(emails).forEach(m -> existing.put(m.getEmail(), m.getId()));

        List<Member> missing = new ArrayList<>();
        for (int i = 0; i < emails.size(); i++) {
            if (!existing.containsKey(emails.get(i))) {
                missing.add(new Member(emails.get(i), "!demo-account-cannot-login", "시연회원" + (i + 1)));
            }
        }
        memberRepository.saveAll(missing).forEach(m -> existing.put(m.getEmail(), m.getId()));
        return emails.stream().map(existing::get).toList();
    }
}
