package com.devticket.apigateway.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.reactive.server.WebTestClient;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@ActiveProfiles("test")
class PublicPathTest {

    @Autowired
    private WebTestClient webTestClient;

    @Test
    void POST_auth_회원가입_인증없이_통과() {
        webTestClient.post()
            .uri("/api/auth/signup")
            .exchange()
            .expectStatus().value(status -> {
                assertThat(status).isNotEqualTo(401);
                assertThat(status).isNotEqualTo(403);
            });
    }

    @Test
    void POST_auth_로그인_인증없이_통과() {
        webTestClient.post()
            .uri("/api/auth/login")
            .exchange()
            .expectStatus().value(status -> {
                assertThat(status).isNotEqualTo(401);
                assertThat(status).isNotEqualTo(403);
            });
    }

    @Test
    void POST_auth_토큰재발급_인증없이_통과() {
        webTestClient.post()
            .uri("/api/auth/reissue")
            .exchange()
            .expectStatus().value(status -> {
                assertThat(status).isNotEqualTo(401);
                assertThat(status).isNotEqualTo(403);
            });
    }

    @Test
    void GET_events_목록조회_인증없이_통과() {
        webTestClient.get()
            .uri("/api/events")
            .exchange()
            .expectStatus().value(status -> {
                assertThat(status).isNotEqualTo(401);
                assertThat(status).isNotEqualTo(403);
            });
    }

    @Test
    void GET_events_검색_인증없이_통과() {
        webTestClient.get()
            .uri("/api/events/search")
            .exchange()
            .expectStatus().value(status -> {
                assertThat(status).isNotEqualTo(401);
                assertThat(status).isNotEqualTo(403);
            });
    }

    @Test
    void GET_events_상세조회_인증없이_통과() {
        webTestClient.get()
            .uri("/api/events/123")
            .exchange()
            .expectStatus().value(status -> {
                assertThat(status).isNotEqualTo(401);
                assertThat(status).isNotEqualTo(403);
            });
    }

    @Test
    void GET_health_인증없이_통과() {
        webTestClient.get()
            .uri("/health")
            .exchange()
            .expectStatus().isOk();
    }

    @Test
    void POST_events_는_인증_필요() {
        // GET은 공개지만 POST는 인증 필요 (판매자 이벤트 생성)
        webTestClient.post()
            .uri("/api/events")
            .exchange()
            .expectStatus().isUnauthorized();
    }
}
