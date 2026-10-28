package com.ticketing.performance;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Performance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private String venue;

    // TEXT 는 오라클에 없는 타입이라 DB 중립적인 길이 지정을 쓴다.
    @Column(length = 2000)
    private String description;

    public Performance(String title, String venue, String description) {
        this.title = title;
        this.venue = venue;
        this.description = description;
    }

    public void update(String title, String venue, String description) {
        this.title = title;
        this.venue = venue;
        this.description = description;
    }
}
