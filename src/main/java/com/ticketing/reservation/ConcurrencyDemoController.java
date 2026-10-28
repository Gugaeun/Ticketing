package com.ticketing.reservation;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** /api/admin/** 이므로 관리자만 호출할 수 있다. */
@RestController
@RequestMapping("/api/admin/demo")
@RequiredArgsConstructor
public class ConcurrencyDemoController {

    private final ConcurrencyDemoService demoService;

    public record DemoRequest(String strategy, int stock, int requests) {}

    @PostMapping("/concurrency")
    public ConcurrencyDemoService.Result run(@RequestBody DemoRequest request) {
        return demoService.run(request.strategy(), request.stock(), request.requests());
    }
}
