package com.devticket.payment.refund.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import com.devticket.payment.refund.domain.enums.RefundStatus;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class RefundTest {

    @Nested
    @DisplayName("create — refundId 자동 생성")
    class CreateTest {

        @Test
        @DisplayName("refundId 가 자동으로 새 UUID 로 발급된다")
        void refundId_자동발급() {
            Refund a = Refund.create(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 1_000, 100);
            Refund b = Refund.create(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 1_000, 100);

            assertThat(a.getRefundId()).isNotNull();
            assertThat(b.getRefundId()).isNotNull();
            assertThat(a.getRefundId()).isNotEqualTo(b.getRefundId());
        }

        @Test
        @DisplayName("초기 상태 REQUESTED, requestedAt 설정")
        void 초기상태() {
            LocalDateTime before = LocalDateTime.now().minusSeconds(1);
            Refund refund = Refund.create(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 1_000, 100);
            LocalDateTime after = LocalDateTime.now().plusSeconds(1);

            assertThat(refund.getStatus()).isEqualTo(RefundStatus.REQUESTED);
            assertThat(refund.getRequestedAt()).isBetween(before, after);
            assertThat(refund.getCompletedAt()).isNull();
            assertThat(refund.getDeletedAt()).isNull();
        }

        @Test
        @DisplayName("orderRefundId 없는 오버로드 — null 로 저장")
        void orderRefundId_생략() {
            Refund refund = Refund.create(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 1_000, 100);
            assertThat(refund.getOrderRefundId()).isNull();
        }
    }

    @Nested
    @DisplayName("createWithId — 외부에서 주어진 refundId 보존")
    class CreateWithIdTest {

        @Test
        @DisplayName("event.refundId 가 그대로 entity 에 저장된다 — Commerce fan-out 보존")
        void refundId_보존() {
            UUID externalRefundId = UUID.randomUUID();
            UUID orderRefundId = UUID.randomUUID();
            UUID orderId = UUID.randomUUID();
            UUID paymentId = UUID.randomUUID();
            UUID userId = UUID.randomUUID();

            Refund refund = Refund.createWithId(
                externalRefundId, orderRefundId, orderId, paymentId, userId, 12_000, 80
            );

            assertThat(refund.getRefundId()).isEqualTo(externalRefundId);
            assertThat(refund.getOrderRefundId()).isEqualTo(orderRefundId);
            assertThat(refund.getOrderId()).isEqualTo(orderId);
            assertThat(refund.getPaymentId()).isEqualTo(paymentId);
            assertThat(refund.getUserId()).isEqualTo(userId);
            assertThat(refund.getRefundAmount()).isEqualTo(12_000);
            assertThat(refund.getRefundRate()).isEqualTo(80);
        }

        @Test
        @DisplayName("동일 refundId 로 두 번 호출해도 entity 의 refundId 는 유지된다")
        void 동일_refundId_반복() {
            UUID id = UUID.randomUUID();
            Refund a = Refund.createWithId(id, null, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 1_000, 100);
            Refund b = Refund.createWithId(id, null, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 1_000, 100);

            assertThat(a.getRefundId()).isEqualTo(id);
            assertThat(b.getRefundId()).isEqualTo(id);
        }

        @Test
        @DisplayName("초기 상태 REQUESTED + requestedAt 설정 — create 와 동일")
        void 초기상태() {
            Refund refund = Refund.createWithId(
                UUID.randomUUID(), null, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 1_000, 100
            );

            assertThat(refund.getStatus()).isEqualTo(RefundStatus.REQUESTED);
            assertThat(refund.getRequestedAt()).isNotNull();
            assertThat(refund.getCompletedAt()).isNull();
        }

        @Test
        @DisplayName("orderRefundId null 허용")
        void orderRefundId_null() {
            Refund refund = Refund.createWithId(
                UUID.randomUUID(), null, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 1_000, 100
            );
            assertThat(refund.getOrderRefundId()).isNull();
        }
    }

    @Nested
    @DisplayName("상태 변경")
    class StatusTransitionTest {

        @Test
        @DisplayName("complete — status COMPLETED + completedAt 설정")
        void complete() {
            Refund refund = Refund.createWithId(
                UUID.randomUUID(), null, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 1_000, 100
            );
            LocalDateTime canceledAt = LocalDateTime.now();

            refund.complete(canceledAt);

            assertThat(refund.getStatus()).isEqualTo(RefundStatus.COMPLETED);
            assertThat(refund.getCompletedAt()).isEqualTo(canceledAt);
        }

        @Test
        @DisplayName("fail — status FAILED")
        void fail() {
            Refund refund = Refund.createWithId(
                UUID.randomUUID(), null, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 1_000, 100
            );

            refund.fail();

            assertThat(refund.getStatus()).isEqualTo(RefundStatus.FAILED);
        }

        @Test
        @DisplayName("approve — status APPROVED")
        void approve() {
            Refund refund = Refund.createWithId(
                UUID.randomUUID(), null, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 1_000, 100
            );

            refund.approve();

            assertThat(refund.getStatus()).isEqualTo(RefundStatus.APPROVED);
        }

        @Test
        @DisplayName("reject — status REJECTED")
        void reject() {
            Refund refund = Refund.createWithId(
                UUID.randomUUID(), null, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 1_000, 100
            );

            refund.reject();

            assertThat(refund.getStatus()).isEqualTo(RefundStatus.REJECTED);
        }

        @Test
        @DisplayName("softDelete — deletedAt 설정")
        void softDelete() {
            Refund refund = Refund.createWithId(
                UUID.randomUUID(), null, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 1_000, 100
            );
            LocalDateTime before = LocalDateTime.now().minusSeconds(1);

            refund.softDelete();

            assertThat(refund.getDeletedAt()).isAfter(before);
        }
    }
}
