package com.devticket.payment.payment.infrastructure.external;

import com.devticket.payment.payment.application.dto.PgPaymentConfirmCommand;
import com.devticket.payment.payment.application.dto.PgPaymentConfirmResult;
import com.devticket.payment.payment.domain.exception.PaymentErrorCode;
import com.devticket.payment.payment.domain.exception.PaymentException;
import com.devticket.payment.payment.infrastructure.external.dto.TossPaymentCancelRequest;
import com.devticket.payment.payment.infrastructure.external.dto.TossPaymentCancelResponse;
import com.devticket.payment.payment.infrastructure.external.dto.TossPaymentConfirmRequest;
import com.devticket.payment.payment.infrastructure.external.dto.TossPaymentConfirmResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Slf4j
@Component
public class PgPaymentClient {

    private static final String CONFIRM_PATH = "/v1/payments/confirm";
    private static final String CANCEL_PATH = "/v1/payments/{paymentKey}/cancel";

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
        TossPaymentCancelRequest request = new TossPaymentCancelRequest(cancelReason);

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

            log.info("PG 결제 취소 성공: paymentKey={}, cancelReason={}, cancelAmount={}",
                paymentKey,
                cancelReason,
                response.totalAmount()
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

    private static String createAuthorizationHeader(String secretKey) {
        String value = secretKey + ":";
        String encoded = Base64.getEncoder()
            .encodeToString(value.getBytes(StandardCharsets.UTF_8));
        return "Basic " + encoded;
    }
}
