# 인수인계 노트 (집에서 이어서 작업하기)

학원/학교 PC에서 만든 내용을 집 PC에서 이어 가기 위한 문서입니다. 저장소: https://github.com/Gugaeun/Ticketing

## 1. 지금까지 된 것

| 영역 | 내용 | 상태 |
|---|---|---|
| 기본 기능 | 회원가입/로그인(JWT), 공연·회차 조회(페이징, 검색), 예매/취소, 내 예매 조회 | 완료, 테스트 통과 |
| 관리자 | 공연/회차 등록·수정·삭제, 티켓 오픈 시각 변경, 판매 현황 대시보드, 동시성 시연 | 완료, 테스트 통과 |
| 동시성 | 락 없음 → 비관적 락 → 낙관적 락(CAS+재시도) 구현과 비교 테스트 | 완료 (H2 기준 수치) |
| 알림 | 예매 완료/취소 이벤트 → 비동기 알림 저장·발송(로그) | 완료, 테스트 통과 |
| 화면 | 예매 사이트 스타일 웹 화면 (`/`), Swagger (`/swagger-ui.html`) | 완료 |
| 문서 | README(ERD, 트러블슈팅, 락 비교표), `docs/benchmark.md` | 완료 (MySQL/k6 수치만 비어 있음) |

H2 기준 측정 결과(재고 100, 동시 요청 1,000): 락 없음 초과 판매 765~900건 / 비관적 락 0건 / 낙관적 락 0건(재시도 약 360~410회).

## DB 결정: 오라클을 주 DB로 사용

- 학원 PC에 오라클 19c가 있고 Docker 설치가 막혀 있어서, **주 DB는 오라클**로 정했다(`oracle` 프로필). 동시성 비교는 H2와 Oracle 19c에서 모두 실행해 같은 경향을 확인했다.
- H2는 기본값(설치 불필요), MySQL은 Docker가 되는 곳에서 선택적으로 쓴다. 코드는 DB에 중립적으로 써서 세 DB 모두 동작한다.
- 오라클 계정은 `scott`을 사용한다(수업용 `MEMBER` 테이블과 충돌하지 않도록 우리 회원 테이블은 `members`).

## 2. 아직 안 한 것

Docker 없이 할 수 있는 것:
1. **오라클 기준 부하 테스트(k6)** — 앱을 `oracle` 프로필로 띄우고 TPS, p95 응답시간, 실패율을 측정해 `docs/benchmark.md`의 2번 표를 채운다. (k6 설치가 막히면 집에서)
2. 인덱스·조회 최적화와 쿼리 개선 기록(실행 계획 비교), 테스트 보강.
3. GitHub Actions CI(H2 기준 테스트).

Docker(또는 Redis 서버)가 필요한 것:
4. **Redis 방식 구현과 비교** — 원자 연산(Lua 스크립트 또는 DECR), 분산 락(Redisson 등).
5. Dockerfile, AWS 배포, 접속 가능한 URL, Testcontainers 통합 테스트.
6. (선택) 대기열, 좌석 선택, 결제 연동(테스트 API).

## 3. 집 PC 준비

### 필요한 것
| 도구 | 설명 |
|---|---|
| **JDK 17** | 필수. Temurin 17 권장. Spring Boot 3은 17 미만에서 실행되지 않는다. |
| **DB** | 집에서도 오라클로 하려면 Oracle Database XE(무료)를 설치하고 `oracle` 프로필의 계정/서비스명을 맞춘다. 설치하지 않으면 기본 H2로 실행된다. |
| **Docker Desktop** | Redis, MySQL, 앱 이미지 빌드에 필요(오라클만 쓰면 선택). 설치 시 `WSL 2` 옵션을 켠다. |
| **IDE (선택)** | 아래 참고. 없어도 터미널만으로 실행할 수 있다. |
| **k6** | 부하 테스트용. https://k6.io (또는 `winget install k6`) |
| **Git** | 저장소 받기. |

Gradle은 따로 설치하지 않아도 된다(저장소의 `gradlew`가 알아서 받는다).

### IDE가 없을 때 (IntelliJ가 설치되어 있지 않아도 된다)
- **IntelliJ IDEA Community** (무료, 추천): 설치 후 `Open`으로 `Ticketing` 폴더를 열면 Gradle 프로젝트로 인식한다. Lombok 플러그인은 기본 포함, `Settings > Build > Compiler > Annotation Processors`에서 `Enable annotation processing`을 켠다.
- **VS Code**: `Extension Pack for Java`와 `Spring Boot Extension Pack`을 설치하면 된다.
- **IDE 없이 터미널만**: 아래 명령으로 충분하다.

### 받아서 실행
```bash
git clone https://github.com/Gugaeun/Ticketing.git
cd Ticketing
```

JDK 17이 기본 java가 아니면 `JAVA_HOME`을 17로 지정한다(PowerShell 예: `$env:JAVA_HOME="C:\Program Files\Eclipse Adoptium\jdk-17..."`).

```bash
# 1) Docker 없이 (H2, 기본)
./gradlew bootRun            # Windows PowerShell: .\gradlew.bat bootRun

# 2) MySQL로 실행
docker compose up -d
./gradlew bootRun --args='--spring.profiles.active=mysql'

# 테스트
./gradlew test
```

- 웹 화면: http://localhost:8080/ , Swagger: http://localhost:8080/swagger-ui.html
- 관리자(개발용 기본값): `admin@ticketing.com` / `admin1234`

## 4. 집에서 할 일 (순서 제안)

1. 위 방법으로 받아서 `./gradlew test`가 통과하는지 먼저 확인한다.
2. (선택) `docker compose up -d` → `mysql` 프로필로 앱을 띄우고 같은 동시성 비교를 MySQL에서도 돌려 DB별 비교를 추가한다. 오라클 PC에서는 웹 화면의 `동시성 시연`으로 바로 비교할 수 있다.
3. k6 부하 테스트(앱을 `oracle` 또는 `mysql` 프로필로 띄운 상태에서):
   ```bash
   k6 run -e BASE_URL=http://localhost:8080 k6/reserve.js
   ```
   `reservation.strategy`를 바꿔 가며(`none`, `pessimistic`, `optimistic`) 같은 시나리오로 반복하고, 결과를 `docs/benchmark.md` 2번 표에 기록한다. 전략 변경은 `application.yml`의 `reservation.strategy` 또는 실행 인자 `--reservation.strategy=none`.
4. Redis 방식을 추가한다 (아래 설계 메모).
5. README의 "진행 상황" 체크리스트와 비교표를 갱신하고 커밋/푸시한다.

## 5. 설계 결정과 주의점 (왜 이렇게 했는지)

- **재고 차감 방식은 인터페이스로 분리**: `ReservationService`(구현: `NoLockReservationService`, `PessimisticLockReservationService`, `OptimisticLockReservationService`). 빈 이름이 곧 전략 이름이고, `reservation.strategy`로 API가 쓸 구현을 고른다. 새 방식은 같은 인터페이스를 구현하고 빈 이름만 붙이면 된다.
- **낙관적 락에 `@Version`을 쓰지 않았다**: 붙이면 "락 없음" 구현도 함께 보호되어 초과 판매를 재현할 수 없다. 대신 `version` 조건부 UPDATE(`decreaseStockIfVersionMatches`)로 같은 원리를 구현했다.
- **기본 DB는 H2, 주 DB는 오라클**: Docker가 막힌 PC에서도 실행되도록 기본값을 H2로 했고, 실제 DB 측정은 `oracle` 프로필(Oracle 19c)로 한다. MySQL은 `mysql` 프로필(Docker)로 선택 사용. H2 수치는 참고용이다.
- **오라클 주의**: `ddl-auto: update`는 이름이 겹치는 기존 테이블에 컬럼을 추가하려 하므로, 수업용 테이블이 있는 스키마에 쓸 때는 테이블 이름 충돌을 확인한다(회원 테이블을 `members`로 한 이유). `TEXT` 같은 DB 전용 타입은 쓰지 않는다.
- **시연용 데이터는 정리한다**: 동시성 시연은 임시 공연/회차/예매와 시연용 회원을 실행 후 지운다.
- **알림은 컨트롤러에서 이벤트 발행**: 예매 트랜잭션이 끝난 뒤에만 발행하고 `@Async`로 처리한다. 동시성 시연/테스트는 서비스를 직접 호출하므로 알림이 생기지 않는다.
- **Gradle 9**: 테스트 실행에 `junit-platform-launcher`가 필요해 `build.gradle`에 선언해 두었다.
- **줄바꿈**: `.gitattributes`로 `gradlew`를 LF로 고정했다(리눅스/WSL 실행용).
- **개발용 기본값 주의**: `application.yml`의 관리자 비밀번호와 JWT 시크릿은 로컬용이다. 배포 시 환경변수(`ADMIN_PASSWORD`, `JWT_SECRET`)로 바꾼다.

## 6. Redis 구현 설계 메모

- 전략 이름 예: `redis-lua`(원자 연산), `redis-lock`(분산 락). 기존 인터페이스를 구현한다.
- **원자 연산**: 회차별 재고 키(`stock:{scheduleId}`)를 Lua 스크립트로 "재고가 있으면 감소"를 한 번에 처리 → 성공하면 DB에 예매를 반영. DB 반영 시점(즉시/비동기)에 따른 정합성 트레이드오프를 README에 정리한다.
- **분산 락**: Redisson 등으로 회차 단위 락을 잡고 기존 DB 차감 로직 실행. 락 획득 비용과 장애 시나리오(락 만료, Redis 장애)를 기록한다.
- **이 PC에서도 기본 실행이 깨지지 않게**: Redis 관련 빈은 `reservation.strategy`나 프로필로 켜질 때만 생성(`@ConditionalOnProperty`)하고, Redis를 쓰는 테스트는 Docker가 없으면 건너뛰도록 한다(`@Testcontainers(disabledWithoutDocker = true)`).
- Redis는 `docker-compose.yml`에 이미 정의되어 있다(`redis:7`, 6379).

## 7. 확인된 사실 vs 확인 안 된 것

- 확인됨: `./gradlew test` 전부 통과(학원 PC, JDK 17, H2). 위 H2 수치.
- 확인 안 됨: MySQL 기준 동작과 수치, k6 결과, Redis 전부, 화면의 모든 브라우저 호환성.
