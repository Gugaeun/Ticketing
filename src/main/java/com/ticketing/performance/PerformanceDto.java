package com.ticketing.performance;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;
import java.util.List;

public final class PerformanceDto {

    private PerformanceDto() {}

    public record PerformanceRequest(@NotBlank String title, @NotBlank String venue, String description) {}

    public record ScheduleRequest(@NotNull LocalDateTime startAt,
                                  @NotNull LocalDateTime ticketOpenAt,
                                  @Min(1) int totalStock) {}

    public record TicketOpenAtRequest(@NotNull LocalDateTime ticketOpenAt) {}

    public record PerformanceSummary(Long id, String title, String venue) {
        static PerformanceSummary from(Performance p) {
            return new PerformanceSummary(p.getId(), p.getTitle(), p.getVenue());
        }
    }

    public record ScheduleResponse(Long id, LocalDateTime startAt, LocalDateTime ticketOpenAt,
                                   int totalStock, int remainingStock) {
        static ScheduleResponse from(Schedule s) {
            return new ScheduleResponse(s.getId(), s.getStartAt(), s.getTicketOpenAt(),
                    s.getTotalStock(), s.getRemainingStock());
        }
    }

    public record PerformanceDetail(Long id, String title, String venue, String description,
                                    List<ScheduleResponse> schedules) {
        static PerformanceDetail from(Performance p, List<Schedule> schedules) {
            return new PerformanceDetail(p.getId(), p.getTitle(), p.getVenue(), p.getDescription(),
                    schedules.stream().map(ScheduleResponse::from).toList());
        }
    }
}
