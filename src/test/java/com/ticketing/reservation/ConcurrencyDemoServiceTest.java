package com.ticketing.reservation;

import com.ticketing.performance.PerformanceRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class ConcurrencyDemoServiceTest {

    @Autowired ConcurrencyDemoService demoService;
    @Autowired PerformanceRepository performanceRepository;
    @Autowired com.ticketing.member.MemberRepository memberRepository;

    @Test
    void 비관적_락과_낙관적_락은_초과_판매가_없고_임시_데이터는_삭제된다() {
        long before = performanceRepository.count();
        long membersBefore = memberRepository.count();

        for (String strategy : new String[]{"pessimistic", "optimistic"}) {
            ConcurrencyDemoService.Result result = demoService.run(strategy, 20, 200);
            assertThat(result.confirmed()).isEqualTo(20);
            assertThat(result.oversold()).isZero();
            assertThat(result.remaining()).isZero();
        }

        assertThat(performanceRepository.count()).isEqualTo(before);
        assertThat(memberRepository.count()).isEqualTo(membersBefore); // 시연용 회원도 정리된다
    }

    @Test
    void 알_수_없는_방식은_거절한다() {
        assertThatThrownBy(() -> demoService.run("unknown", 10, 10)).isInstanceOf(IllegalStateException.class);
    }
}
