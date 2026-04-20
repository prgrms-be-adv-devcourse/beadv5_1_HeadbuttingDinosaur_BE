package org.example.ai.domain.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.example.ai.common.exception.ErrorCode;

@Getter
@RequiredArgsConstructor
public enum AiErrorCode implements ErrorCode {

    // ========== Common-like for AI ==========
    INVALID_INPUT(400, "AI_001", "입력값이 올바르지 않습니다."),
    USER_VECTOR_NOT_FOUND(404, "AI_002", "사용자 벡터를 찾을 수 없습니다."),
    EVENT_EMBEDDING_NOT_FOUND(404, "AI_003", "이벤트 임베딩을 찾을 수 없습니다."),

    // ========== Elasticsearch / Recommendation ==========
    USER_VECTOR_READ_FAILED(500, "AI_004", "사용자 벡터 조회에 실패했습니다."),
    USER_VECTOR_SAVE_FAILED(500, "AI_005", "사용자 벡터 저장에 실패했습니다."),
    EVENT_INDEX_SEARCH_FAILED(500, "AI_006", "이벤트 인덱스 검색에 실패했습니다."),
    RECOMMENDATION_FAILED(500, "AI_007", "추천 생성에 실패했습니다."),
    VECTOR_UPDATE_FAILED(500, "AI_008", "사용자 벡터 갱신에 실패했습니다."),

    // ========== Internal API / External dependency ==========
    INTERNAL_API_BAD_GATEWAY(502, "AI_009", "연동 서비스 처리에 실패했습니다."),
    INTERNAL_API_UNAVAILABLE(503, "AI_010", "연동 서비스를 일시적으로 이용할 수 없습니다."),
    OPENAI_EMBEDDING_FAILED(502, "AI_011", "임베딩 생성에 실패했습니다."),
    OPENAI_EMBEDDING_TIMEOUT(503, "AI_012", "임베딩 서비스를 일시적으로 이용할 수 없습니다."),

    // ========== Kafka / Async ==========
    ACTION_LOG_CONSUME_FAILED(500, "AI_013", "행동 로그 처리에 실패했습니다."),
    EVENT_DOCUMENT_CONSUME_FAILED(500, "AI_014", "이벤트 문서 처리에 실패했습니다."),

    // ========== Fallback ==========
    INTERNAL_SERVER_ERROR(500, "AI_999", "서버 내부 오류가 발생했습니다.");

    private final int status;
    private final String code;
    private final String message;
}
