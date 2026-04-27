package com.devticket.payment.refund.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import com.devticket.payment.payment.domain.enums.PaymentMethod;
import com.devticket.payment.payment.domain.model.Payment;
import com.devticket.payment.payment.domain.repository.PaymentRepository;
import com.devticket.payment.payment.infrastructure.client.CommerceInternalClient;
import com.devticket.payment.payment.infrastructure.client.dto.InternalOrderItemInfoResponse;
import com.devticket.payment.refund.application.service.RefundService;
import com.devticket.payment.refund.domain.exception.RefundErrorCode;
import com.devticket.payment.refund.domain.exception.RefundException;
import com.devticket.payment.refund.domain.model.OrderRefund;
import com.devticket.payment.refund.domain.repository.OrderRefundRepository;
import com.devticket.payment.refund.domain.repository.RefundRepository;
import com.devticket.payment.refund.domain.repository.RefundTicketRepository;
import com.devticket.payment.refund.infrastructure.client.EventInternalClient;
import com.devticket.payment.refund.infrastructure.client.dto.InternalEventInfoResponse;
import com.devticket.payment.refund.presentation.dto.PgRefundRequest;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * refundPgTicket 동시성 통합 테스트
 *
 * 실제 DB(H2) UNIQUE 제약 + dedup 가드 검증.
 * CommerceInternalClient / EventInternalClient 만 Mock (외부 HTTP 호출 차단).
 */
@SpringBootTest
@ActiveProfiles("test")
class RefundPgTicketConcurrencyIntegrationTest {

    @Autowired private RefundService refundService;
    @Autowired private PaymentRepository paymentRepository;
    @Autowired private OrderRefundRepository orderRefundRepository;
    @Autowired private RefundRepository refundRepository;
    @Autowired private RefundTicketRepository refundTicketRepository;

    @MockitoBean private CommerceInternalClient commerceInternalClient;
    @MockitoBean private EventInternalClient eventInternalClient;

    private UUID userId;
    private String ticketId;
    private Payment payment;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        ticketId = UUID.randomUUID().toString();

        payment = Payment.create(UUID.randomUUID(), userId, PaymentMethod.PG, 50_000);
        payment.approve("payment-key-test");
        paymentRepository.save(payment);

        // upsertOrderRefund 내부 INSERT 경합 제거를 위해 OrderRefund 선행 생성
        orderRefundRepository.save(OrderRefund.create(
            payment.getOrderId(), userId, payment.getPaymentId(),
            PaymentMethod.PG, 50_000, 1
        ));

        InternalEventInfoResponse eventInfo = new InternalEventInfoResponse(
            UUID.randomUUID(), UUID.randomUUID(), "테스트 이벤트", 50_000,
            "ACTIVE", "CONCERT", 100, 4, 50,
            LocalDateTime.now().plusDays(10).toString(),
            LocalDateTime.now().minusDays(5).toString(),
            LocalDateTime.now().plusDays(5).toString()
        );

        given(commerceInternalClient.getOrderItemInfoByTicketId(ticketId))
            .willReturn(new InternalOrderItemInfoResponse(
                UUID.fromString(ticketId), payment.getOrderId(), userId,
                eventInfo.eventId(), 50_000
            ));
        given(eventInternalClient.getEventInfo(any())).willReturn(eventInfo);
    }

    // =========================================================
    // 테스트 1: 동일 ticketId 동시 10건 — RefundTicket 1건만 생성
    //
    // 공격: 환불 버튼 연타 / 네트워크 재전송 시뮬레이션
    // 방어 1차: existsByTicketId 선행 체크 → REFUND_ALREADY_IN_PROGRESS(409)
    // 방어 2차: refund_ticket.ticket_id UNIQUE 제약 + DataIntegrityViolationException catch
    // 검증: 성공 1건, 나머지 REFUND_ALREADY_IN_PROGRESS, DB Refund·RefundTicket 각 1건
    // =========================================================
    @Test
    @DisplayName("동일 ticketId로 동시 10건 요청 시 RefundTicket·Refund는 각 1건만 생성된다")
    void 동일_ticketId_동시요청_1건만_생성() throws InterruptedException {
        int threadCount = 10;
        PgRefundRequest request = new PgRefundRequest("단순 변심");

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch ready = new CountDownLatch(threadCount);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threadCount);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger alreadyInProgressCount = new AtomicInteger(0);
        AtomicInteger otherErrorCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                ready.countDown();
                try {
                    start.await();
                    refundService.refundPgTicket(userId, ticketId, request);
                    successCount.incrementAndGet();
                } catch (RefundException e) {
                    if (e.getErrorCode() == RefundErrorCode.REFUND_ALREADY_IN_PROGRESS) {
                        alreadyInProgressCount.incrementAndGet();
                    } else {
                        otherErrorCount.incrementAndGet();
                        System.out.println("  기타 RefundException: " + e.getErrorCode());
                    }
                } catch (Exception e) {
                    otherErrorCount.incrementAndGet();
                    System.out.println("  기타 에러: " + e.getClass().getSimpleName() + " — " + e.getMessage());
                } finally {
                    done.countDown();
                }
            });
        }

        ready.await();
        start.countDown();
        done.await();
        executor.shutdown();

        long refundCount = refundRepository
            .findByUserId(userId, PageRequest.of(0, 100))
            .getTotalElements();
        boolean ticketExists = refundTicketRepository.existsByTicketId(UUID.fromString(ticketId));

        System.out.println("========== refundPgTicket 동시성 테스트 결과 ==========");
        System.out.println("성공: " + successCount.get() + "건");
        System.out.println("REFUND_ALREADY_IN_PROGRESS: " + alreadyInProgressCount.get() + "건");
        System.out.println("기타 에러: " + otherErrorCount.get() + "건");
        System.out.println("DB Refund 수: " + refundCount + "건");
        System.out.println("DB RefundTicket 존재: " + ticketExists);

        assertThat(successCount.get()).isEqualTo(1);
        assertThat(alreadyInProgressCount.get()).isEqualTo(threadCount - 1);
        assertThat(otherErrorCount.get()).isEqualTo(0);
        assertThat(refundCount).isEqualTo(1);
        assertThat(ticketExists).isTrue();
    }
}
