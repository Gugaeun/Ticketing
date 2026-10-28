# 공연 티켓 선착순 예매 서비스 (Spring Boot 포트폴리오)

## 1. 프로젝트 정의

**한 줄 소개:** 인기 공연 티켓 오픈 시 수천 명이 동시에 몰려도 초과 판매나 중복 예매 없이 처리하는 예매 서비스

**핵심 스토리:** 동시성 문제를 일부러 재현하고, 단계별로 개선하면서 수치로 비교한다.

**원칙:** 작게 만들고 깊게 판다. 기능 수보다 "문제 → 원인 → 해결 → 수치"가 핵심.

## 2. MVP 기능

- 회원가입/로그인 (Spring Security + JWT)
- 공연/회차 조회 (페이징, 검색)
- 티켓 예매 및 취소 (선착순, 재고 제한)
- 내 예매 내역 조회
- 관리자: 공연 등록, 티켓 오픈 시간 설정

**확장 (여유 있으면):** 좌석 선택, 결제 연동(테스트 API), 대기열, 예매 완료 알림, Kafka/Redis Queue 기반 비동기 예매 처리

## 3. ERD 초안

```
member        (id, email, password, name, created_at)
performance   (id, title, venue, description)
schedule      (id, performance_id, start_at, ticket_open_at, total_stock, remaining_stock, version)
reservation   (id, member_id, schedule_id, quantity, status[CONFIRMED/CANCELED], created_at)
              └ unique(member_id, schedule_id)  ← 중복 예매 방지
```

- `schedule.remaining_stock`이 동시성 실습의 핵심 지점
- `schedule.version`은 낙관적 락용

## 4. 개발 로드맵 (4~6주)

| 단계 | 내용 | 산출물 |
|---|---|---|
| 1주차 | 프로젝트 세팅, 도메인/ERD, 회원·공연 CRUD, JWT | 기본 API, Swagger |
| 2주차 | 예매 로직 **락 없이** 구현 후 부하 테스트 | 초과 판매 재현 결과 (문제 증명) |
| 3주차 | 비관적 락 → 낙관적 락 적용 및 비교 | TPS/응답시간/실패율 비교표 |
| 4주차 | Redis 도입 (분산 락 또는 Lua 스크립트로 재고 차감) | 개선 수치, 트레이드오프 정리 |
| 5주차 | 테스트 코드(Testcontainers), 인덱스·조회 최적화 | 동시성 테스트, 쿼리 개선 기록 |
| 6주차 | Docker, CI/CD, AWS 배포, README 정리 | 접속 가능한 URL |

## 5. 기술 스택

- **Backend:** Java 17+, Spring Boot 3.x, Spring Data JPA, QueryDSL
- **DB/캐시:** MySQL (또는 PostgreSQL), Redis
- **테스트:** JUnit5, Testcontainers, k6 (부하 테스트)
- **인프라:** Docker Compose, GitHub Actions, AWS EC2/RDS
- **문서:** Swagger 또는 Spring REST Docs, ERD (dbdiagram.io)

> 미정: 빌드 도구(Gradle/Maven), Java 버전, DB(MySQL/PostgreSQL) → 프로젝트 세팅 시 확정

## 6. 락 방식별 비교 계획

| 방식 | 구현 | 확인할 점 |
|---|---|---|
| 락 없음 | 단순 재고 차감 | 초과 판매 재현 |
| 비관적 락 | `@Lock(PESSIMISTIC_WRITE)` | 정합성, 대기 시간, 데드락 |
| 낙관적 락 | `@Version` + 재시도 | 충돌 시 실패율, 재시도 비용 |
| Redis 분산 락 | Redisson 등 | 락 획득 비용, 장애 시나리오 |
| Redis 원자 연산 | Lua 스크립트 / DECR | 최고 TPS, DB 반영 시점 정합성 |

각 방식마다 동일 시나리오(예: 재고 100, 동시 요청 1,000)로 k6 부하 테스트 후 TPS, p95 응답시간, 실패율, 초과 판매 여부를 기록한다.

## 7. README에 반드시 포함할 것

1. 프로젝트 소개와 아키텍처 다이어그램
2. ERD
3. **트러블슈팅:** 문제 상황 → 원인 → 해결 → 결과 수치
4. 락 방식별 성능 비교표
5. 실행 방법, API 문서 링크, 배포 URL

## 8. Claude Code에서 시작할 때 첫 작업 제안

1. 프로젝트 초기화: 패키지 구조, `build.gradle`, `docker-compose.yml` (MySQL + Redis)
2. 엔티티/레포지토리: `Member`, `Performance`, `Schedule`, `Reservation`
3. 락 없는 예매 서비스 + 동시성 테스트로 초과 판매 재현
4. k6 부하 테스트 스크립트 작성
5. 이후 락 방식을 하나씩 추가하며 결과 기록 (`docs/benchmark.md`)
