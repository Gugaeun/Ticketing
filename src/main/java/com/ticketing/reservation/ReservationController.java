package com.ticketing.reservation;

import com.ticketing.notification.ReservationEvents;
import com.ticketing.performance.Schedule;
import com.ticketing.performance.ScheduleRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/reservations")
public class ReservationController {

    private final ReservationService reservationService;
    private final ReservationRepository reservationRepository;
    private final ScheduleRepository scheduleRepository;
    private final ApplicationEventPublisher events;

    /** reservation.strategy 값(빈 이름)에 해당하는 구현을 사용한다. 기본값은 pessimistic. */
    public ReservationController(Map<String, ReservationService> strategies,
                                 @Value("${reservation.strategy}") String strategy,
                                 ReservationRepository reservationRepository,
                                 ScheduleRepository scheduleRepository,
                                 ApplicationEventPublisher events) {
        ReservationService selected = strategies.get(strategy);
        if (selected == null) {
            throw new IllegalStateException("알 수 없는 reservation.strategy: " + strategy + " " + strategies.keySet());
        }
        this.reservationService = selected;
        this.reservationRepository = reservationRepository;
        this.scheduleRepository = scheduleRepository;
        this.events = events;
    }

    public record ReserveRequest(@NotNull Long scheduleId, @Min(1) int quantity) {}

    @PostMapping
    public Long reserve(@AuthenticationPrincipal Long memberId, @Valid @RequestBody ReserveRequest request) {
        Long reservationId = reservationService.reserve(memberId, request.scheduleId(), request.quantity());
        // 예매 트랜잭션이 커밋된 뒤에만 발행한다. 알림은 비동기로 처리되어 응답을 지연시키지 않는다.
        events.publishEvent(new ReservationEvents.Confirmed(memberId, reservationId, request.scheduleId(), request.quantity()));
        return reservationId;
    }

    @DeleteMapping("/{id}")
    public void cancel(@AuthenticationPrincipal Long memberId, @PathVariable Long id) {
        Reservation reservation = reservationRepository.findById(id).orElse(null);
        reservationService.cancel(memberId, id);
        if (reservation != null) {
            events.publishEvent(new ReservationEvents.Canceled(
                    memberId, id, reservation.getScheduleId(), reservation.getQuantity()));
        }
    }

    /** 예매 내역 + 공연 정보. 기존 Reservation 필드는 그대로 두고 화면에 필요한 공연명·장소·일시를 덧붙인다. */
    public record MyReservation(Long id, Long memberId, Long scheduleId, int quantity, ReservationStatus status,
                                LocalDateTime createdAt, String performanceTitle, String venue, LocalDateTime startAt) {}

    @GetMapping
    public List<MyReservation> mine(@AuthenticationPrincipal Long memberId) {
        List<Reservation> reservations = reservationRepository.findByMemberId(memberId);
        Map<Long, Schedule> schedules = scheduleRepository
                .findAllWithPerformanceByIdIn(reservations.stream().map(Reservation::getScheduleId).distinct().toList())
                .stream().collect(Collectors.toMap(Schedule::getId, Function.identity()));
        return reservations.stream().map(r -> {
            Schedule s = schedules.get(r.getScheduleId());
            return new MyReservation(r.getId(), r.getMemberId(), r.getScheduleId(), r.getQuantity(), r.getStatus(),
                    r.getCreatedAt(), s == null ? null : s.getPerformance().getTitle(),
                    s == null ? null : s.getPerformance().getVenue(), s == null ? null : s.getStartAt());
        }).toList();
    }
}
