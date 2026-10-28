package com.ticketing.reservation;

import com.ticketing.notification.ReservationEvents;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/reservations")
public class ReservationController {

    private final ReservationService reservationService;
    private final ReservationRepository reservationRepository;
    private final ApplicationEventPublisher events;

    /** reservation.strategy 값(빈 이름)에 해당하는 구현을 사용한다. 기본값은 pessimistic. */
    public ReservationController(Map<String, ReservationService> strategies,
                                 @Value("${reservation.strategy}") String strategy,
                                 ReservationRepository reservationRepository,
                                 ApplicationEventPublisher events) {
        ReservationService selected = strategies.get(strategy);
        if (selected == null) {
            throw new IllegalStateException("알 수 없는 reservation.strategy: " + strategy + " " + strategies.keySet());
        }
        this.reservationService = selected;
        this.reservationRepository = reservationRepository;
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

    @GetMapping
    public List<Reservation> mine(@AuthenticationPrincipal Long memberId) {
        return reservationRepository.findByMemberId(memberId);
    }
}
