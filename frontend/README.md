# Ticketing 프런트엔드 (React)

Ticketing 백엔드(Spring Boot)의 API를 호출하는 React + TypeScript 화면입니다. **로컬 실행 기준**이며 배포는 범위에 포함하지 않았습니다.

## 실행

백엔드를 먼저 켭니다(`http://localhost:8080`). 그다음:

```bash
cd frontend
npm install
npm run dev      # http://localhost:5173
```

백엔드 주소를 바꾸려면 `VITE_API_BASE` 환경 변수를 사용합니다 (기본값 `http://localhost:8080`).
백엔드의 CORS 설정이 `http://localhost:5173`을 허용하므로 프록시 없이 직접 호출합니다.

## 구현한 화면

- 회원가입 / 로그인 (JWT를 `localStorage`에 저장하고 `Authorization: Bearer` 헤더로 전송)
- 공연 목록: 제목 검색, 페이징, 회차별 잔여 좌석 (`GET /api/performances/with-schedules`)
- 예매: 품절·중복 예매(409), 미로그인(401), 티켓 오픈 전 상태를 화면에 표시
- 내 예매: 공연명·장소·일시 표시, 예매 취소

## 알려진 한계

- 토큰을 `localStorage`에 저장하므로 XSS에 취약합니다. 학습용 프로젝트라 이 방식을 사용했습니다.
- 알림, 관리자 대시보드, 동시성 시연 화면은 React로 옮기지 않았습니다. 이 기능들은 백엔드가 제공하는 기존 웹 화면(`http://localhost:8080/`)에서 사용합니다.
- 자동화된 프런트엔드 테스트는 없습니다. 브라우저에서 가입 → 예매 → 중복 예매 → 내 예매 → 취소 흐름을 직접 확인했습니다.
