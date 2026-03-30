package com.devticket.payment.common.idempotency;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class IdempotencyService {

    private final IdempotencyKeyRepository idempotencyKeyRepository;
    private final ObjectMapper objectMapper;

    /**
     * 이미 처리된 키인지 확인하고, 존재하면 저장된 응답을 반환한다.
     */
    public Optional<IdempotencyKey> findExisting(String key) {
        return idempotencyKeyRepository.findByKey(key);
    }

    /**
     * 새 멱등성 키와 응답을 저장한다.
     * 반드시 비즈니스 로직과 같은 트랜잭션 안에서 호출해야 한다.
     */
    public void save(String key, Object responseBody, int httpStatus) {
        try {
            String json = objectMapper.writeValueAsString(responseBody);
            IdempotencyKey idempotencyKey = IdempotencyKey.create(key, json, httpStatus);
            idempotencyKeyRepository.save(idempotencyKey);
        } catch (JsonProcessingException e) {
            log.error("[Idempotency] 응답 직렬화 실패 — key={}", key, e);
            throw new IllegalStateException("멱등성 키 응답 직렬화 실패", e);
        }
    }

    /**
     * 저장된 응답을 지정된 타입으로 역직렬화한다.
     */
    public <T> T deserializeResponse(IdempotencyKey idempotencyKey, Class<T> type) {
        try {
            return objectMapper.readValue(idempotencyKey.getResponseBody(), type);
        } catch (JsonProcessingException e) {
            log.error("[Idempotency] 응답 역직렬화 실패 — key={}", idempotencyKey.getKey(), e);
            throw new IllegalStateException("멱등성 키 응답 역직렬화 실패", e);
        }
    }
}
