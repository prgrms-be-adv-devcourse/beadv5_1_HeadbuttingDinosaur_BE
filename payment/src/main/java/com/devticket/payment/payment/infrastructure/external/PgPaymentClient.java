package com.devticket.payment.payment.infrastructure.external;

import com.devticket.payment.payment.application.dto.PgPaymentCancelCommand;
import com.devticket.payment.payment.application.dto.PgPaymentCancelResult;
import com.devticket.payment.payment.application.dto.PgPaymentConfirmCommand;
import com.devticket.payment.payment.application.dto.PgPaymentConfirmResult;
import com.devticket.payment.payment.domain.exception.PaymentErrorCode;
import com.devticket.payment.payment.domain.exception.PaymentException;
import com.devticket.payment.payment.infrastructure.external.dto.TossPaymentCancelRequest;
import com.devticket.payment.payment.infrastructure.external.dto.TossPaymentCancelResponse;
import com.devticket.payment.payment.infrastructure.external.dto.TossPaymentConfirmRequest;
import com.devticket.payment.payment.infrastructure.external.dto.TossPaymentConfirmResponse;
import com.devticket.payment.payment.infrastructure.external.dto.TossPaymentStatusResponse;
import com.devticket.payment.refund.domain.exception.RefundErrorCode;
import com.devticket.payment.refund.domain.exception.RefundException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Slf4j
@Component
public class PgPaymentClient {

    private static final String CONFIRM_PATH = "/v1/payments/confirm";
    private static final String CANCEL_PATH = "/v1/payments/{paymentKey}/cancel";
    private static final String STATUS_BY_ORDER_PATH = "/v1/payments/orders/{orderId}";

    private final RestClient restClient;

    public PgPaymentClient(
        RestClient.Builder restClientBuilder,
        @Value("${pg.toss.base-url}") String baseUrl,
        @Value("${pg.toss.secret-key}") String secretKey
    ) {
        this.restClient = restClientBuilder
            .baseUrl(baseUrl)
            .defaultHeader(HttpHeaders.AUTHORIZATION, createAuthorizationHeader(secretKey))
            .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .build();
    }

    public PgPaymentConfirmResult confirm(PgPaymentConfirmCommand command) {
        TossPaymentConfirmRequest request = new TossPaymentConfirmRequest(
            command.paymentKey(),
            command.orderId(),
            command.amount()
        );

        try {
            TossPaymentConfirmResponse response = restClient.post()
                .uri(CONFIRM_PATH)
                .body(request)
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError, (req, res) -> {
                    HttpStatusCode status = res.getStatusCode();
                    if (status == HttpStatus.UNAUTHORIZED || status == HttpStatus.FORBIDDEN) {
                        throw new PaymentException(PaymentErrorCode.PG_CONFIRM_FAILED);
                    }
                    throw new PaymentException(PaymentErrorCode.INVALID_PAYMENT_REQUEST);
                })
                .onStatus(HttpStatusCode::is5xxServerError, (req, res) -> {
                    throw new PaymentException(PaymentErrorCode.PG_CONFIRM_FAILED);
                })
                .body(TossPaymentConfirmResponse.class);

            if (response == null) {
                throw new PaymentException(PaymentErrorCode.PG_CONFIRM_FAILED);
            }

            log.info(
                "PG 결제 승인 성공: orderId={}, paymentKey={}, method={}, amount={}",
                command.orderId(),
                response.paymentKey(),
                response.method(),
                response.totalAmount()
            );

            return new PgPaymentConfirmResult(
                response.paymentKey(),
                response.orderId(),
                response.method(),
                response.status(),
                response.totalAmount(),
                response.approvedAt()
            );

        } catch (ResourceAccessException e) {
            log.error(
                "PG 결제 승인 타임아웃/네트워크 오류: orderId={}, paymentKey={}, message={}",
                command.orderId(),
                command.paymentKey(),
                e.getMessage()
            );
            throw new PaymentException(PaymentErrorCode.PG_TIMEOUT);

        } catch (RestClientResponseException e) {
            log.error(
                "PG 결제 승인 실패: orderId={}, paymentKey={}, statusCode={}, responseBody={}",
                command.orderId(),
                command.paymentKey(),
                e.getStatusCode(),
                e.getResponseBodyAsString()
            );
            throw new PaymentException(PaymentErrorCode.PG_CONFIRM_FAILED);
        }
    }

    public void cancel(String paymentKey, String cancelReason) {
        TossPaymentCancelRequest request = new TossPaymentCancelRequest(cancelReason, null);

        try {
            TossPaymentCancelResponse response = restClient.post()
                .uri(CANCEL_PATH, paymentKey)
                .body(request)
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError, (req, res) -> {
                    HttpStatusCode status = res.getStatusCode();
                    if (status == HttpStatus.UNAUTHORIZED || status == HttpStatus.FORBIDDEN) {
                        throw new PaymentException(PaymentErrorCode.PG_CANCEL_FAILED);
                    }
                    throw new PaymentException(PaymentErrorCode.INVALID_PAYMENT_REQUEST);
                })
                .onStatus(HttpStatusCode::is5xxServerError, (req, res) -> {
                    throw new PaymentException(PaymentErrorCode.PG_CANCEL_FAILED);
                })
                .body(TossPaymentCancelResponse.class);

            if (response == null) {
                throw new PaymentException(PaymentErrorCode.PG_CANCEL_FAILED);
            }

            TossPaymentCancelResponse.Cancels latestCancel =
                response.cancels()[response.cancels().length - 1];

            log.info("PG 결제 취소 성공: paymentKey={}, cancelReason={}, cancelAmount={}",
                paymentKey,
                cancelReason,
                latestCancel.cancelAmount()
            );

        } catch (PaymentException e) {
            throw e;

        } catch (ResourceAccessException e) {
            log.error("PG 결제 취소 타임아웃/네트워크 오류: paymentKey={}, message={}",
                paymentKey,
                e.getMessage()
            );
            throw new PaymentException(PaymentErrorCode.PG_CANCEL_FAILED);

        } catch (RestClientResponseException e) {
            log.error("PG 결제 취소 실패: paymentKey={}, statusCode={}, responseBody={}",
                paymentKey,
                e.getStatusCode(),
                e.getResponseBodyAsString()
            );
            throw new PaymentException(PaymentErrorCode.PG_CANCEL_FAILED);
        }
    }

    /**
     * orderId(=chargeId)로 Toss 결제 상태 조회 — 사후 보정 스케줄러용
     * @return DONE/CANCELED/ABORTED 등 상태가 담긴 응답. 404(Toss 미도달)이면 Optional.empty()
     */
    public Optional<TossPaymentStatusResponse> findPaymentByOrderId(String orderId) {
        try {
            TossPaymentStatusResponse response = restClient.get()
                .uri(STATUS_BY_ORDER_PATH, orderId)
                .retrieve()
                .onStatus(status -> status == HttpStatus.NOT_FOUND, (req, res) -> {
                    // Toss에 해당 orderId 결제 없음 → 빈 Optional 반환을 위해 커스텀 예외 사용
                    throw new PaymentException(PaymentErrorCode.INVALID_PAYMENT_REQUEST);
                })
                .onStatus(HttpStatusCode::is4xxClientError, (req, res) -> {
                    throw new PaymentException(PaymentErrorCode.PG_CONFIRM_FAILED);
                })
                .onStatus(HttpStatusCode::is5xxServerError, (req, res) -> {
                    throw new PaymentException(PaymentErrorCode.PG_CONFIRM_FAILED);
                })
                .body(TossPaymentStatusResponse.class);

            return Optional.ofNullable(response);

        } catch (PaymentException e) {
            if (e.getErrorCode() == PaymentErrorCode.INVALID_PAYMENT_REQUEST) {
                // 404 케이스 — Toss에 결제 없음
                log.warn("[PG] orderId 조회 404 — orderId={}", orderId);
                return Optional.empty();
            }
            throw e;

        } catch (ResourceAccessException e) {
            log.error("[PG] orderId 조회 타임아웃 — orderId={}, message={}", orderId, e.getMessage());
            throw new PaymentException(PaymentErrorCode.PG_TIMEOUT);

        } catch (RestClientResponseException e) {
            log.error("[PG] orderId 조회 실패 — orderId={}, status={}", orderId, e.getStatusCode());
            throw new PaymentException(PaymentErrorCode.PG_CONFIRM_FAILED);
        }
    }

    //부분 환불
    public PgPaymentCancelResult cancelPartial(PgPaymentCancelCommand command) {

        TossPaymentCancelRequest request = new TossPaymentCancelRequest(
            command.cancelReason(),
            command.cancelAmount()
        );

        try {
            TossPaymentCancelResponse response = restClient.post()
                .uri(CANCEL_PATH, command.paymentKey())
                .headers(headers -> {
                    if (command.idempotencyKey() != null && !command.idempotencyKey().isBlank()) {
                        headers.set("Idempotency-Key", command.idempotencyKey());
                    }
                })
                .body(request)
                .retrieve()
                .body(TossPaymentCancelResponse.class);

            if (response == null || response.cancels() == null || response.cancels().length == 0) {
                throw new RefundException(RefundErrorCode.PG_REFUND_FAILED);
            }

            // 마지막 cancel = 이번 환불
            TossPaymentCancelResponse.Cancels latestCancel =
                response.cancels()[response.cancels().length - 1];

            // 누적 환불 금액 계산
            int totalCanceledAmount = Arrays.stream(response.cancels())
                .mapToInt(TossPaymentCancelResponse.Cancels::cancelAmount)
                .sum();

            log.info("PG 부분 환불 성공: paymentKey={}, cancelAmount={}, totalCanceledAmount={}",
                response.paymentKey(),
                latestCancel.cancelAmount(),
                totalCanceledAmount
            );

            return new PgPaymentCancelResult(
                response.paymentKey(),
                latestCancel.cancelAmount(),
                totalCanceledAmount,
                latestCancel.canceledAt()
            );

        } catch (HttpClientErrorException e) {
            throw new RefundException(RefundErrorCode.REFUND_INVALID_REQUEST);

        } catch (Exception e) {
            log.error("PG 환불 실패", e);
            throw new RefundException(RefundErrorCode.PG_REFUND_FAILED);
        }
    }

    private static String createAuthorizationHeader(String secretKey) {
        String value = secretKey + ":";
        String encoded = Base64.getEncoder()
            .encodeToString(value.getBytes(StandardCharsets.UTF_8));
        return "Basic " + encoded;
    }
}
