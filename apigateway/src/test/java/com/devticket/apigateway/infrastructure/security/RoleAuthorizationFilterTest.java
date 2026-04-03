package com.devticket.apigateway.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;

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
class RoleAuthorizationFilterTest {

    @Autowired
    private WebTestClient webTestClient;

    // ──────────────────────────────────────────────
    // USER 접근 제어
    // ──────────────────────────────────────────────

    @Test
    void USER가_seller_API_접근시_403_COMMON_005() {
        String token = JwtTestHelper.createValidToken("1", "user@test.com", "USER");

        webTestClient.get()
            .uri("/api/seller/events")
            .header("Authorization", "Bearer " + token)
            .exchange()
            .expectStatus().isForbidden()
            .expectBody()
            .jsonPath("$.status").isEqualTo(403)
            .jsonPath("$.code").isEqualTo("COMMON_005")
            .jsonPath("$.message").isEqualTo("접근 권한이 없습니다.");
    }

    @Test
    void USER가_admin_API_접근시_403_COMMON_005() {
        String token = JwtTestHelper.createValidToken("1", "user@test.com", "USER");

        webTestClient.get()
            .uri("/api/admin/users")
            .header("Authorization", "Bearer " + token)
            .exchange()
            .expectStatus().isForbidden()
            .expectBody()
            .jsonPath("$.code").isEqualTo("COMMON_005");
    }

    @Test
    void USER가_일반_API_접근시_통과() {
        String token = JwtTestHelper.createValidToken("1", "user@test.com", "USER");

        webTestClient.get()
            .uri("/api/cart")
            .header("Authorization", "Bearer " + token)
            .exchange()
            .expectStatus().value(status -> {
                assertThat(status).isNotEqualTo(401);
                assertThat(status).isNotEqualTo(403);
            });
    }

    // ──────────────────────────────────────────────
    // SELLER 접근 제어
    // ──────────────────────────────────────────────

    @Test
    void SELLER가_seller_API_접근시_통과() {
        String token = JwtTestHelper.createValidToken("2", "seller@test.com", "SELLER");

        webTestClient.get()
            .uri("/api/seller/events")
            .header("Authorization", "Bearer " + token)
            .exchange()
            .expectStatus().value(status -> {
                assertThat(status).isNotEqualTo(401);
                assertThat(status).isNotEqualTo(403);
            });
    }

    @Test
    void SELLER가_admin_API_접근시_403() {
        String token = JwtTestHelper.createValidToken("2", "seller@test.com", "SELLER");

        webTestClient.get()
            .uri("/api/admin/users")
            .header("Authorization", "Bearer " + token)
            .exchange()
            .expectStatus().isForbidden()
            .expectBody()
            .jsonPath("$.code").isEqualTo("COMMON_005");
    }

    @Test
    void SELLER가_일반_API_접근시_통과() {
        String token = JwtTestHelper.createValidToken("2", "seller@test.com", "SELLER");

        webTestClient.get()
            .uri("/api/orders")
            .header("Authorization", "Bearer " + token)
            .exchange()
            .expectStatus().value(status -> {
                assertThat(status).isNotEqualTo(401);
                assertThat(status).isNotEqualTo(403);
            });
    }

    // ──────────────────────────────────────────────
    // ADMIN 접근 제어
    // ──────────────────────────────────────────────

    @Test
    void ADMIN이_admin_API_접근시_통과() {
        String token = JwtTestHelper.createValidToken("99", "admin@test.com", "ADMIN");

        webTestClient.get()
            .uri("/api/admin/users")
            .header("Authorization", "Bearer " + token)
            .exchange()
            .expectStatus().value(status -> {
                assertThat(status).isNotEqualTo(401);
                assertThat(status).isNotEqualTo(403);
            });
    }

    @Test
    void ADMIN이_seller_API_접근시_통과() {
        String token = JwtTestHelper.createValidToken("99", "admin@test.com", "ADMIN");

        webTestClient.get()
            .uri("/api/seller/events")
            .header("Authorization", "Bearer " + token)
            .exchange()
            .expectStatus().value(status -> {
                assertThat(status).isNotEqualTo(401);
                assertThat(status).isNotEqualTo(403);
            });
    }

    @Test
    void ADMIN이_일반_API_접근시_통과() {
        String token = JwtTestHelper.createValidToken("99", "admin@test.com", "ADMIN");

        webTestClient.get()
            .uri("/api/cart")
            .header("Authorization", "Bearer " + token)
            .exchange()
            .expectStatus().value(status -> {
                assertThat(status).isNotEqualTo(401);
                assertThat(status).isNotEqualTo(403);
            });
    }

    @Test
    void 토큰_없이_seller_경로_접근시_차단됨() {
        webTestClient.get()
            .uri("/api/seller/events")
            .exchange()
            .expectStatus().value(status -> {
                assertThat(status).isIn(401, 403);
            });
    }

    // 권한 부족 테스트는 유효한 토큰 + 잘못된 role로 해야 함
    @Test
    void USER가_seller_API_접근시_403() {
        String token = JwtTestHelper.createValidToken("1", "user@test.com", "USER");
        webTestClient.get()
            .uri("/api/seller/events/1")
            .header("Authorization", "Bearer " + token)
            .exchange()
            .expectStatus().isForbidden()
            .expectBody()
            .jsonPath("$.code").isEqualTo("COMMON_005");
    }

    @Test
    void USER가_admin_API_접근시_403() {
        String token = JwtTestHelper.createValidToken("1", "user@test.com", "USER");
        webTestClient.get()
            .uri("/api/admin/users")
            .header("Authorization", "Bearer " + token)
            .exchange()
            .expectStatus().isForbidden()
            .expectBody()
            .jsonPath("$.code").isEqualTo("COMMON_005");
    }
}
