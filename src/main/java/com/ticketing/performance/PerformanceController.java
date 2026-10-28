package com.ticketing.performance;

import com.ticketing.performance.PerformanceDto.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/performances")
@RequiredArgsConstructor
public class PerformanceController {

    private final PerformanceService performanceService;

    @GetMapping
    public Page<PerformanceSummary> search(@RequestParam(required = false) String keyword,
                                           @PageableDefault(size = 10, sort = "id") Pageable pageable) {
        return performanceService.search(keyword, pageable);
    }

    /** 목록 + 회차를 한 번에 (화면용). 공연마다 상세를 따로 호출하는 N+1 요청을 없앤다. */
    @GetMapping("/with-schedules")
    public Page<PerformanceDetail> searchWithSchedules(@RequestParam(required = false) String keyword,
                                                       @PageableDefault(size = 10, sort = "id") Pageable pageable) {
        return performanceService.searchWithSchedules(keyword, pageable);
    }

    @GetMapping("/{id}")
    public PerformanceDetail get(@PathVariable Long id) {
        return performanceService.get(id);
    }
}
