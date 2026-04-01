package com.devticket.apigateway.support;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import javax.crypto.SecretKey;

public class JwtTestHelper {

    private static final String SECRET = "test-jwt-secret-key-devticket-2025-must-be-256-bits";
    private static final SecretKey SECRET_KEY =
        Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));

    /**
     * 유효한 토큰 생성.
     */
    public static String createValidTokenWithProfile(String userId, String email, String role, boolean profileCompleted) {
        return Jwts.builder()
            .subject(userId)
            .claim("email", email)
            .claim("role", role)
            .claim("profileCompleted", profileCompleted)
            .issuedAt(new Date())
            .expiration(new Date(System.currentTimeMillis() + 1000 * 60 * 30))
            .signWith(SECRET_KEY)
            .compact();
    }

    public static String createValidToken(String userId, String email, String role) {
        return createValidTokenWithProfile(userId, email, role, true);
    }

    /**
     * 이미 만료된 토큰 생성.
     */
    public static String createExpiredToken(String userId, String email, String role) {
        return Jwts.builder()
            .subject(userId)
            .claim("email", email)
            .claim("role", role)
            .issuedAt(new Date(System.currentTimeMillis() - 3600000))
            .expiration(new Date(System.currentTimeMillis() - 1800000))
            .signWith(SECRET_KEY)
            .compact();
    }

    /**
     * 다른 키로 서명한 위조 토큰 생성.
     */
    public static String createInvalidSignatureToken(String userId, String email, String role) {
        SecretKey wrongKey = Keys.hmacShaKeyFor(
            "wrong-secret-key-devticket-2025-totally-different-key"
                .getBytes(StandardCharsets.UTF_8));
        return Jwts.builder()
            .subject(userId)
            .claim("email", email)
            .claim("role", role)
            .issuedAt(new Date())
            .expiration(new Date(System.currentTimeMillis() + 1800000))
            .signWith(wrongKey)
            .compact();
    }
}
