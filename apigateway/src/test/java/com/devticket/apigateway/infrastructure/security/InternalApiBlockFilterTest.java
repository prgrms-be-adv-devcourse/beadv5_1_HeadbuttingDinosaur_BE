package com.devticket.apigateway.infrastructure.security;

import com.devticket.apigateway.support.JwtTestHelper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.reactive.server.WebTestClient;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@ActiveProfiles("test")
class InternalApiBlockFilterTest {

    @Autowired
    private WebTestClient webTestClient;

    @Test
    void internal_경로_토큰없이_접근시_403() {
        webTestClient.get()
            .uri("/internal/members/1")
            .exchange()
            .expectStatus().isForbidden()
            .expectBody()
            .jsonPath("$.status").isEqualTo(403)
            .jsonPath("$.code").isEqualTo("COMMON_005")
            .jsonPath("$.message").isEqualTo("접근 권한이 없습니다.");
    }

    @Test
    void internal_경로_유효한_토큰으로_접근시에도_403() {
        String token = JwtTestHelper.createValidToken("99", "admin@test.com", "ADMIN");

        webTestClient.get()
            .uri("/internal/members/1")
            .header("Authorization", "Bearer " + token)
            .exchange()
            .expectStatus().isForbidden()
            .expectBody()
            .jsonPath("$.code").isEqualTo("COMMON_005");
    }

    @Test
    void internal_events_경로_차단() {
        webTestClient.get()
            .uri("/internal/events/1/validate-purchase")
            .exchange()
            .expectStatus().isForbidden();
    }

    @Test
    void internal_orders_경로_차단() {
        webTestClient.get()
            .uri("/internal/orders/1")
            .exchange()
            .expectStatus().isForbidden();
    }

    @Test
    void internal_payments_경로_차단() {
        webTestClient.get()
            .uri("/internal/payments/by-order/1")
            .exchange()
            .expectStatus().isForbidden();
    }

    @Test
    void internal_wallets_경로_차단() {
        webTestClient.get()
            .uri("/internal/wallets/1/balance")
            .exchange()
            .expectStatus().isForbidden();
    }
}
