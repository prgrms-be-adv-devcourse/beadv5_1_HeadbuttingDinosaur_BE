-- DevTicket Log Module — action_log 복합 인덱스 추가
-- 목적: AI 조회 엔드포인트(GET /internal/logs/actions) 쿼리 최적화
--       user_id 기준 시간범위 역순 조회 (user별 recentVector 갱신 배치)
-- 참조: docs/actionLog.md §5.5
-- 실행 주체: 수동 DDL (ShedLock 테이블 실행 절차와 동일)
-- 주의: CONCURRENTLY 옵션 — 운영 중 테이블 쓰기 락 회피. 트랜잭션 블록 내 실행 금지.

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_action_log_user_created
  ON log.action_log (user_id, created_at DESC);

COMMENT ON INDEX log.idx_action_log_user_created
  IS 'AI 조회 엔드포인트용 — user별 created_at 역순 조회 (docs/actionLog.md §5.5)';
