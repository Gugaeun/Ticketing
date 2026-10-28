package com.ticketing.performance;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/** 화면 확인용 샘플 공연. 공연이 하나도 없을 때만 넣고, seed.enabled=false 로 끌 수 있다. */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "seed.enabled", havingValue = "true", matchIfMissing = true)
public class SampleDataInitializer implements ApplicationRunner {

    private final PerformanceRepository performanceRepository;
    private final ScheduleRepository scheduleRepository;

    private LocalDateTime now;

    @Override
    public void run(ApplicationArguments args) {
        if (performanceRepository.count() > 0) {
            return;
        }
        now = LocalDateTime.now().withMinute(0).withSecond(0).withNano(0);

        // 예매 중
        Performance concert = perf("SUMMER NIGHT 라이브 콘서트", "올림픽공원 올림픽홀", "여름밤을 채우는 밴드 라이브. 앙코르 포함 120분.");
        schedule(concert, 30, 19, -1, 100);
        schedule(concert, 31, 18, -1, 3);

        Performance musical = perf("뮤지컬 〈별을 그리는 사람〉", "샤롯데씨어터", "화가와 천문학자의 시간을 넘나드는 이야기. 전석 지정석.");
        schedule(musical, 45, 20, -1, 50);
        schedule(musical, 46, 14, -1, 50);
        schedule(musical, 46, 19, -1, 50);

        Performance jazz = perf("서울 재즈 페스티벌 2026", "올림픽공원 88잔디마당", "국내외 재즈 아티스트 30팀이 함께하는 3일간의 축제.");
        schedule(jazz, 20, 15, -2, 300);
        schedule(jazz, 21, 15, -2, 300);

        Performance play = perf("연극 〈마지막 기차〉", "대학로 아트원씨어터", "막차를 기다리는 세 사람의 하룻밤. 러닝타임 90분.");
        schedule(play, 12, 19, -1, 40);
        schedule(play, 13, 15, -1, 12);

        Performance hiphop = perf("HIPHOP PLAYA 2026", "KBS아레나", "한국 힙합 신의 주역들이 한자리에. 스탠딩 전석.");
        schedule(hiphop, 25, 18, -1, 150);

        Performance recital = perf("피아노 리사이틀 · 쇼팽을 만나다", "롯데콘서트홀", "쇼팽 녹턴과 발라드 전곡. 인터미션 15분 포함.");
        schedule(recital, 18, 19, -3, 80);

        // 매진
        Performance idol = perf("STARLIGHT 팬미팅 〈첫 번째 밤〉", "잠실실내체육관", "데뷔 1주년 기념 팬미팅. 전 회차 매진되었습니다.");
        soldOut(schedule(idol, 14, 18, -5, 30));

        Performance orchestra = perf("신년 음악회 · 빈 필하모닉 레퍼토리", "예술의전당 콘서트홀", "왈츠와 폴카로 여는 새해. 1회 매진, 2회 잔여 소수.");
        soldOut(schedule(orchestra, 70, 17, -5, 20));
        schedule(orchestra, 71, 17, -5, 6);

        // 오픈 예정
        Performance classic = perf("가을 클래식 페스티벌", "예술의전당 콘서트홀", "오케스트라와 함께하는 가을 저녁. 티켓 오픈 예정.");
        schedule(classic, 60, 17, 3, 200);

        Performance rock = perf("ROCK THE NIGHT 2026", "인천 송도 달빛축제공원", "록 밴드 12팀의 야외 공연. 얼리버드 오픈 예정.");
        schedule(rock, 80, 16, 5, 500);

        Performance ballet = perf("발레 〈호두까기 인형〉", "세종문화회관 대극장", "연말을 수놓는 클래식 발레. 오픈 예정.");
        schedule(ballet, 90, 19, 7, 120);
        schedule(ballet, 91, 15, 7, 120);
    }

    private Performance perf(String title, String venue, String description) {
        return performanceRepository.save(new Performance(title, venue, description));
    }

    /** daysFromNow 일 뒤 hour 시 공연. 티켓 오픈은 현재 기준 openOffsetDays 일(음수면 이미 오픈). */
    private Schedule schedule(Performance p, int daysFromNow, int hour, int openOffsetDays, int stock) {
        return scheduleRepository.save(new Schedule(
                p, now.plusDays(daysFromNow).withHour(hour), now.plusDays(openOffsetDays), stock));
    }

    private void soldOut(Schedule schedule) {
        schedule.decrease(schedule.getTotalStock());
        scheduleRepository.save(schedule);
    }
}
