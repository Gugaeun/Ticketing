package com.ticketing.performance;

import com.ticketing.performance.PerformanceDto.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminPerformanceController {

    private final PerformanceService performanceService;

    @PostMapping("/performances")
    @ResponseStatus(HttpStatus.CREATED)
    public Long create(@Valid @RequestBody PerformanceRequest request) {
        return performanceService.create(request);
    }

    @PutMapping("/performances/{id}")
    public void update(@PathVariable Long id, @Valid @RequestBody PerformanceRequest request) {
        performanceService.update(id, request);
    }

    @DeleteMapping("/performances/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        performanceService.delete(id);
    }

    @PostMapping("/performances/{id}/schedules")
    @ResponseStatus(HttpStatus.CREATED)
    public Long addSchedule(@PathVariable Long id, @Valid @RequestBody ScheduleRequest request) {
        return performanceService.addSchedule(id, request);
    }

    @PatchMapping("/schedules/{id}/ticket-open-at")
    public void changeTicketOpenAt(@PathVariable Long id, @Valid @RequestBody TicketOpenAtRequest request) {
        performanceService.changeTicketOpenAt(id, request);
    }
}
