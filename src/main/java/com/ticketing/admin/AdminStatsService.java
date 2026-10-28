package com.ticketing.admin;

import com.ticketing.performance.Performance;
import com.ticketing.performance.PerformanceRepository;
import com.ticketing.performance.Schedule;
import com.ticketing.performance.ScheduleRepository;
import com.ticketing.reservation.ReservationRepository;
import com.ticketing.reservation.ReservationStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AdminStatsService {

    public record ScheduleStat(Long id, LocalDateTime startAt, LocalDateTime ticketOpenAt,
                               int totalStock, int remainingStock, long sold) {}

    public record PerformanceStat(Long id, String title, String venue, long totalStock, long sold,
                                  List<ScheduleStat> schedules) {}

    public record Stats(long performances, long schedules, long totalStock, long sold,
                        long confirmedReservations, List<PerformanceStat> items) {}

    private final PerformanceRepository performanceRepository;
    private final ScheduleRepository scheduleRepository;
    private final ReservationRepository reservationRepository;

    /** sold 는 재고 카운터가 아니라 확정 예매 기록(수량 합)에서 센다. 두 값이 다르면 정합성 문제의 신호다. */
    @Transactional(readOnly = true)
    public Stats stats() {
        Map<Long, Long> soldBySchedule = new HashMap<>();
        for (Object[] row : reservationRepository.sumQuantityBySchedule(ReservationStatus.CONFIRMED)) {
            soldBySchedule.put((Long) row[0], ((Number) row[1]).longValue());
        }
        Map<Long, List<Schedule>> schedulesByPerformance = scheduleRepository.findAll().stream()
                .collect(Collectors.groupingBy(s -> s.getPerformance().getId()));

        List<PerformanceStat> items = performanceRepository.findAll().stream()
                .map(p -> toStat(p, schedulesByPerformance.getOrDefault(p.getId(), List.of()), soldBySchedule))
                .toList();

        return new Stats(
                items.size(),
                items.stream().mapToLong(i -> i.schedules().size()).sum(),
                items.stream().mapToLong(PerformanceStat::totalStock).sum(),
                items.stream().mapToLong(PerformanceStat::sold).sum(),
                reservationRepository.countByStatus(ReservationStatus.CONFIRMED),
                items);
    }

    private PerformanceStat toStat(Performance p, List<Schedule> schedules, Map<Long, Long> soldBySchedule) {
        List<ScheduleStat> stats = schedules.stream()
                .map(s -> new ScheduleStat(s.getId(), s.getStartAt(), s.getTicketOpenAt(),
                        s.getTotalStock(), s.getRemainingStock(), soldBySchedule.getOrDefault(s.getId(), 0L)))
                .toList();
        return new PerformanceStat(p.getId(), p.getTitle(), p.getVenue(),
                stats.stream().mapToLong(ScheduleStat::totalStock).sum(),
                stats.stream().mapToLong(ScheduleStat::sold).sum(),
                stats);
    }
}
