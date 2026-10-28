-- 오라클(scott 스키마)에서 인덱스 전/후 실행 계획을 비교하는 스크립트.
-- SQL Developer 에서 한 블록씩 실행(F5 스크립트 실행)하고, 결과(실행 계획과 소요 시간)를 docs/query-optimization.md 에 기록한다.
-- 주의: 테스트 데이터는 schedule_id 900000 이상만 사용하고, 마지막 "정리" 블록으로 지운다. 실제 데이터는 건드리지 않는다.

-- ============================================================
-- 0) 테스트 데이터 20만 건 생성 (회차 50개에 4,000건씩)
-- ============================================================
INSERT INTO reservation (created_at, member_id, quantity, schedule_id, status)
SELECT SYSTIMESTAMP, 900000 + LEVEL, 1, 900000 + MOD(LEVEL, 50),
       CASE WHEN MOD(LEVEL, 10) = 0 THEN 'CANCELED' ELSE 'CONFIRMED' END
FROM dual CONNECT BY LEVEL <= 200000;
COMMIT;

-- ============================================================
-- 1) 인덱스 없이 실행 계획 확인 (먼저 인덱스를 지워서 "전" 상태를 만든다)
-- ============================================================
DROP INDEX idx_reservation_schedule_status;
EXEC DBMS_STATS.GATHER_TABLE_STATS(USER, 'RESERVATION');

EXPLAIN PLAN FOR
SELECT COUNT(*) FROM reservation WHERE schedule_id = 900007 AND status = 'CONFIRMED';
SELECT * FROM TABLE(DBMS_XPLAN.DISPLAY);

-- 소요 시간(경과 시간)도 함께 기록한다. SQL Developer 에서 아래를 실행하고 하단의 경과 시간을 본다.
SELECT COUNT(*) FROM reservation WHERE schedule_id = 900007 AND status = 'CONFIRMED';

-- ============================================================
-- 2) 인덱스를 만든 뒤 다시 확인 ("후")
-- ============================================================
CREATE INDEX idx_reservation_schedule_status ON reservation (schedule_id, status);
EXEC DBMS_STATS.GATHER_TABLE_STATS(USER, 'RESERVATION');

EXPLAIN PLAN FOR
SELECT COUNT(*) FROM reservation WHERE schedule_id = 900007 AND status = 'CONFIRMED';
SELECT * FROM TABLE(DBMS_XPLAN.DISPLAY);

SELECT COUNT(*) FROM reservation WHERE schedule_id = 900007 AND status = 'CONFIRMED';

-- ============================================================
-- 3) 정리 (테스트 데이터 삭제, 인덱스는 앱이 쓰는 상태로 남긴다)
-- ============================================================
DELETE FROM reservation WHERE schedule_id >= 900000;
COMMIT;
EXEC DBMS_STATS.GATHER_TABLE_STATS(USER, 'RESERVATION');
