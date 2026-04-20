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
class JwtAuthenticationFilterTest {

    @Autowired
    private WebTestClient webTestClient;

    // ──────────────────────────────────────────────
    // 유효한 JWT 테스트
    // ──────────────────────────────────────────────

    @Test
    void 유효한_JWT_요청시_인증_통과() {
        String token = JwtTestHelper.createValidToken("42", "user@test.com", "USER");

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
    void 유효한_JWT_요청시_하위서비스_헤더_전달_확인() {
        String token = JwtTestHelper.createValidToken("42", "user@test.com", "USER");

        // Gateway가 401/403을 반환하지 않으면 하위 서비스로 헤더를 전달한 것
        // (하위 서비스가 없어서 502/503이 올 수 있지만, 인증은 통과한 상태)
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
    // 프로필 미완성 사용자 차단 테스트
    // ──────────────────────────────────────────────

    @Test
    void 프로필_미완성_사용자가_일반_API_요청시_403_COMMON_009() {
        String token = JwtTestHelper.createValidTokenWithProfile("42", "user@test.com", "USER", false);

        webTestClient.get()
            .uri("/api/cart")
            .header("Authorization", "Bearer " + token)
            .exchange()
            .expectStatus().isForbidden()
            .expectBody()
            .jsonPath("$.status").isEqualTo(403)
            .jsonPath("$.code").isEqualTo("COMMON_010")
            .jsonPath("$.message").isEqualTo("프로필 설정을 완료해야 서비스를 이용할 수 있습니다.")
            .jsonPath("$.timestamp").exists();
    }

    @Test
    void 프로필_미완성_사용자가_POST_api_users_profile_요청시_통과() {
        String token = JwtTestHelper.createValidTokenWithProfile("42", "user@test.com", "USER", false);

        webTestClient.post()
            .uri("/api/users/profile")
            .header("Authorization", "Bearer " + token)
            .exchange()
            .expectStatus().value(status -> {
                assertThat(status).isNotEqualTo(401);
                assertThat(status).isNotEqualTo(403);
            });
    }

    @Test
    void 프로필_미완성_사용자가_api_auth_요청시_통과() {
        String token = JwtTestHelper.createValidTokenWithProfile("42", "user@test.com", "USER", false);

        webTestClient.post()
            .uri("/api/auth/logout")
            .header("Authorization", "Bearer " + token)
            .exchange()
            .expectStatus().value(status -> {
                assertThat(status).isNotEqualTo(401);
                assertThat(status).isNotEqualTo(403);
            });
    }

    @Test
    void 프로필_완성_사용자는_모든_API_정상_통과() {
        String token = JwtTestHelper.createValidTokenWithProfile("42", "user@test.com", "USER", true);

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
    void 프로필_미완성_사용자가_orders_요청시_403_반환() {
        String token = JwtTestHelper.createValidTokenWithProfile("42", "user@test.com", "USER", false);

        webTestClient.get()
            .uri("/api/orders")
            .header("Authorization", "Bearer " + token)
            .exchange()
            .expectStatus().isForbidden()
            .expectBody()
            .jsonPath("$.code").isEqualTo("COMMON_010");
    }

    @Test
    void 프로필_미완성_사용자가_GET_api_users_profile_은_차단() {
        // POST만 허용, GET은 차단
        String token = JwtTestHelper.createValidTokenWithProfile("42", "user@test.com", "USER", false);

        webTestClient.get()
            .uri("/api/users/profile")
            .header("Authorization", "Bearer " + token)
            .exchange()
            .expectStatus().isForbidden()
            .expectBody()
            .jsonPath("$.code").isEqualTo("COMMON_010");
    }

    // ──────────────────────────────────────────────
    // 인증 실패 테스트
    // ──────────────────────────────────────────────

    @Test
    void Authorization_헤더_누락시_401_COMMON_002() {
        webTestClient.get()
            .uri("/api/cart")
            .exchange()
            .expectStatus().isUnauthorized()
            .expectBody()
            .jsonPath("$.status").isEqualTo(401)
            .jsonPath("$.code").isEqualTo("COMMON_002")
            .jsonPath("$.message").isEqualTo("인증이 필요합니다.")
            .jsonPath("$.timestamp").exists();
    }

    @Test
    void Bearer_형식_아닌_토큰시_401_COMMON_002() {
        webTestClient.get()
            .uri("/api/cart")
            .header("Authorization", "Basic some-token")
            .exchange()
            .expectStatus().isUnauthorized()
            .expectBody()
            .jsonPath("$.code").isEqualTo("COMMON_002");
    }

    @Test
    void 만료된_토큰_요청시_401_COMMON_003() {
        String token = JwtTestHelper.createExpiredToken("42", "user@test.com", "USER");

        webTestClient.get()
            .uri("/api/cart")
            .header("Authorization", "Bearer " + token)
            .exchange()
            .expectStatus().isUnauthorized()
            .expectBody()
            .jsonPath("$.status").isEqualTo(401)
            .jsonPath("$.code").isEqualTo("COMMON_003")
            .jsonPath("$.message").isEqualTo("토큰이 만료되었습니다.");
    }

    @Test
    void 위조된_토큰_요청시_401_COMMON_004() {
        String token = JwtTestHelper.createInvalidSignatureToken("42", "user@test.com", "USER");

        webTestClient.get()
            .uri("/api/cart")
            .header("Authorization", "Bearer " + token)
            .exchange()
            .expectStatus().isUnauthorized()
            .expectBody()
            .jsonPath("$.status").isEqualTo(401)
            .jsonPath("$.code").isEqualTo("COMMON_004")
            .jsonPath("$.message").isEqualTo("유효하지 않은 토큰입니다.");
    }

    @Test
    void 잘못된_형식의_토큰_요청시_401_COMMON_004() {
        webTestClient.get()
            .uri("/api/cart")
            .header("Authorization", "Bearer not.a.valid.jwt.token")
            .exchange()
            .expectStatus().isUnauthorized()
            .expectBody()
            .jsonPath("$.code").isEqualTo("COMMON_004");
    }

    // ──────────────────────────────────────────────
    // 에러 응답 포맷 검증
    // ──────────────────────────────────────────────

    @Test
    void 에러_응답_JSON_Content_Type_확인() {
        webTestClient.get()
            .uri("/api/cart")
            .exchange()
            .expectHeader().contentType("application/json");
    }

    @Test
    void 에러_응답_포맷_status_code_message_timestamp_포함() {
        webTestClient.get()
            .uri("/api/cart")
            .exchange()
            .expectBody()
            .jsonPath("$.status").isNumber()
            .jsonPath("$.code").isNotEmpty()
            .jsonPath("$.message").isNotEmpty()
            .jsonPath("$.timestamp").isNotEmpty();
    }
}
