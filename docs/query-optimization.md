# 조회 최적화 기록

예매 쓰기 경로(락 방식 비교)와 별개로, 읽기 쪽에서 비효율이 있는 곳을 찾아 고친 기록이다.
**측정 전인 항목은 "측정 전"으로 표시했고, 측정한 뒤에 숫자를 채운다.**

## 1. 무엇을 살펴봤나

| 조회 | 쿼리 | 문제 후보 |
|---|---|---|
| 회차별 확정 예매 수/판매량 집계 | `where schedule_id = ? and status = ?`, `group by schedule_id` | `reservation`의 unique 인덱스는 `(member_id, schedule_id)`라서 `schedule_id`로 시작하는 조회에는 쓸 수 없다 → 전체 스캔 |
| 공연의 회차 목록 | `where performance_id = ? order by start_at` | 오라클은 외래키 컬럼에 인덱스를 자동으로 만들지 않는다(MySQL은 만든다) |
| 공연 목록 화면 | 목록 1번 + 공연마다 상세 호출 | 공연이 N개면 HTTP 요청 N+1번, SQL 2N+1번 (N+1 문제) |
| 내 예매 목록 | `where member_id = ?` | 기존 unique 인덱스 `(member_id, schedule_id)`가 이미 `member_id` 선두라서 **추가 인덱스 불필요** |
| 알림 목록 | `where member_id = ? order by id desc` | 이미 `(member_id, id)` 인덱스가 있음 |

## 2. 변경 내용

### 2-1. 인덱스 2개 추가
- `idx_reservation_schedule_status (schedule_id, status)` — 회차별 확정 예매 집계, 시연/통계 조회
- `idx_schedule_performance_start (performance_id, start_at)` — 공연의 회차 목록(조건 + 정렬을 한 인덱스로)

### 2-2. 공연 목록 N+1 제거
- 이전: 화면이 `GET /api/performances` 후 공연마다 `GET /api/performances/{id}`를 호출
- 이후: `GET /api/performances/with-schedules` 한 번에 공연과 회차를 받는다. 서버는 공연 한 페이지를 읽은 뒤 **회차를 `IN` 조건 한 번**(`join fetch`)으로 가져와 공연별로 묶는다.
- SQL 실행 횟수는 `PerformanceQueryCountTest`가 센다(Hibernate Statistics).

## 3. 검토했지만 하지 않은 것
- **공연 제목 검색(`LIKE '%키워드%'`)에 일반 인덱스**: 앞에 `%`가 붙은 검색은 B-tree 인덱스를 쓸 수 없어 효과가 없다. 개선하려면 접두사 검색(`LIKE '키워드%'`)으로 규칙을 바꾸거나 전문 검색(오라클 Text 등)이 필요한데, 데이터가 수십 건 규모라 지금은 하지 않았다.
- **쓰기 경로 영향**: 새 인덱스는 예매 INSERT마다 갱신 비용이 조금 더 든다(트레이드오프). 이 때문에 k6 반복 측정은 인덱스를 추가한 **이후의 스키마**에서 다시 한다(`k6/repeat.ps1`).

## 4. 측정 결과

### 4-1. SQL 실행 횟수 (`PerformanceQueryCountTest`, H2, 샘플 공연 11개, 2026-10-28)
Hibernate Statistics의 `prepareStatementCount`로 센 값이다.

| | SQL 실행 횟수 | 구성 |
|---|---|---|
| 개선 전 (목록 + 공연마다 상세) | **23회** | 목록 1 + 공연 11개 × (공연 조회 1 + 회차 조회 1) |
| 개선 후 (`with-schedules`) | **2회** | 공연 한 페이지 1 + 회차 `IN` 조회 1 |

- 공연이 N개면 개선 전은 `1 + 2N`회로 늘어나고, 개선 후는 (공연 페이지, 필요 시 건수, 회차 `IN` 조회)로 **N과 무관하게 최대 3회**다.
- HTTP 요청 수도 `1 + N`회에서 1회로 줄었다(화면이 목록과 회차를 한 번에 받는다).
- 샘플 공연이 11개인 이유: 테스트 실행 순서에 따라 다른 테스트가 만든 데이터가 섞여 12개가 아닐 수 있다. 위 값은 그 실행의 공연 수 기준이다.

### 4-2. 인덱스 전/후 실행 계획 (Oracle 19c, 예매 20만 건)
측정 전. [`docs/sql/explain-plan.sql`](sql/explain-plan.sql)을 SQL Developer에서 실행해 결과를 채운다.

| | 실행 계획 | 경과 시간 |
|---|---|---|
| 인덱스 없음 | 측정 전 (예상: TABLE ACCESS FULL) | 측정 전 |
| `idx_reservation_schedule_status` | 측정 전 (예상: INDEX RANGE SCAN) | 측정 전 |

"예상"은 가설이며, 실제 계획과 다르면 실제 결과를 적는다. 데이터가 적으면 옵티마이저가 인덱스를 쓰지 않고 전체 스캔을 고를 수 있어서 20만 건으로 확인한다.
