package com.ticketing.performance;

import com.ticketing.performance.PerformanceDto.PerformanceDetail;
import com.ticketing.performance.PerformanceDto.PerformanceSummary;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import static org.assertj.core.api.Assertions.assertThat;

/** 공연 목록(+회차) 조회의 SQL 실행 횟수를 세어 N+1 제거 효과를 기록한다. */
@SpringBootTest(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
class PerformanceQueryCountTest {

    @Autowired PerformanceService service;
    @Autowired EntityManagerFactory entityManagerFactory;

    @Test
    void 목록과_회차를_조회해도_쿼리는_3번_이하다() {
        Statistics stats = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();

        // 개선 전: 목록 1번 + 공연마다 상세(공연 조회 + 회차 조회) 호출
        stats.clear();
        Page<PerformanceSummary> list = service.search(null, PageRequest.of(0, 50));
        list.forEach(p -> service.get(p.id()));
        long before = stats.getPrepareStatementCount();

        // 개선 후: 목록과 회차를 한 번에
        stats.clear();
        Page<PerformanceDetail> withSchedules = service.searchWithSchedules(null, PageRequest.of(0, 50));
        long after = stats.getPrepareStatementCount();

        System.out.printf("[쿼리 수] 공연 %d개 기준: 개선 전 %d회 → 개선 후 %d회%n",
                list.getContent().size(), before, after);

        assertThat(withSchedules.getContent()).hasSameSizeAs(list.getContent());
        assertThat(after).isLessThanOrEqualTo(3);
        assertThat(before).isGreaterThan(after);
    }
}
