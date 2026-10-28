package com.ticketing.performance;

import com.ticketing.common.NotFoundException;
import com.ticketing.performance.PerformanceDto.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PerformanceService {

    private final PerformanceRepository performanceRepository;
    private final ScheduleRepository scheduleRepository;

    @Transactional(readOnly = true)
    public Page<PerformanceSummary> search(String keyword, Pageable pageable) {
        Page<Performance> page = (keyword == null || keyword.isBlank())
                ? performanceRepository.findAll(pageable)
                : performanceRepository.findByTitleContainingIgnoreCase(keyword.trim(), pageable);
        return page.map(PerformanceSummary::from);
    }

    @Transactional(readOnly = true)
    public PerformanceDetail get(Long id) {
        Performance performance = find(id);
        return PerformanceDetail.from(performance, scheduleRepository.findByPerformanceIdOrderByStartAt(id));
    }

    @Transactional
    public Long create(PerformanceRequest request) {
        return performanceRepository.save(
                new Performance(request.title(), request.venue(), request.description())).getId();
    }

    @Transactional
    public void update(Long id, PerformanceRequest request) {
        find(id).update(request.title(), request.venue(), request.description());
    }

    @Transactional
    public void delete(Long id) {
        find(id);
        scheduleRepository.deleteByPerformanceId(id);
        performanceRepository.deleteById(id);
    }

    @Transactional
    public Long addSchedule(Long performanceId, ScheduleRequest request) {
        Performance performance = find(performanceId);
        return scheduleRepository.save(new Schedule(
                performance, request.startAt(), request.ticketOpenAt(), request.totalStock())).getId();
    }

    @Transactional
    public void changeTicketOpenAt(Long scheduleId, TicketOpenAtRequest request) {
        scheduleRepository.findById(scheduleId)
                .orElseThrow(() -> new NotFoundException("존재하지 않는 회차입니다."))
                .changeTicketOpenAt(request.ticketOpenAt());
    }

    private Performance find(Long id) {
        return performanceRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("존재하지 않는 공연입니다."));
    }
}
