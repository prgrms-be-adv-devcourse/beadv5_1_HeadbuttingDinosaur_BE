-- DevTicket Log Module — action_log 테이블 생성
-- 스키마: log (다른 서비스 스키마와 격리)

CREATE SCHEMA IF NOT EXISTS log;

CREATE TABLE log.action_log (
  id                 BIGSERIAL       PRIMARY KEY,
  user_id            UUID            NOT NULL,
  event_id           UUID,
  action_type        VARCHAR(20)     NOT NULL,
  search_keyword     VARCHAR(255),
  stack_filter       VARCHAR(255),
  dwell_time_seconds INT,
  quantity           INT,
  total_amount       BIGINT,
  created_at         TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
  updated_at         TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

-- 인덱스
CREATE INDEX idx_action_log_user_id     ON log.action_log (user_id);
CREATE INDEX idx_action_log_event_id    ON log.action_log (event_id);
CREATE INDEX idx_action_log_action_type ON log.action_log (action_type);
CREATE INDEX idx_action_log_created_at  ON log.action_log (created_at);

COMMENT ON TABLE  log.action_log                    IS '사용자 행동 로그';
COMMENT ON COLUMN log.action_log.user_id            IS '사용자 외부 식별키 (UUID)';
COMMENT ON COLUMN log.action_log.event_id           IS '이벤트 외부 식별키 (UUID), VIEW 시 NULL';
COMMENT ON COLUMN log.action_log.action_type        IS 'VIEW|DETAIL_VIEW|CART_ADD|CART_REMOVE|PURCHASE|DWELL_TIME|REFUND';
COMMENT ON COLUMN log.action_log.dwell_time_seconds IS 'DWELL_TIME 시 체류 시간(초)';
COMMENT ON COLUMN log.action_log.quantity           IS 'PURCHASE, CART_ADD 시 수량';
COMMENT ON COLUMN log.action_log.total_amount       IS 'PURCHASE, REFUND 시 금액 (PURCHASE 다건 주문은 NULL — SUM 부풀림 방지. 정확한 매출은 Payment.payment.total_amount 집계)';
COMMENT ON COLUMN log.action_log.created_at         IS '이벤트 발생 시각 (Kafka 메시지 timestamp 저장, AI 시퀀스 분석 기준) — 수신 시각 아님';
COMMENT ON COLUMN log.action_log.updated_at         IS '현재 미사용 — action_log는 append-only 로그 특성상 UPDATE 없음. 향후 확장 여지로 보존';
