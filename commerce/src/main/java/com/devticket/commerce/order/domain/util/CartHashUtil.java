package com.devticket.commerce.order.domain.util;

import com.devticket.commerce.cart.domain.model.CartItem;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public class CartHashUtil {

    private CartHashUtil() {}

    // (eventId, quantity) 리스트를 eventId 기준 오름차순 정렬 후 SHA-256
    // unitPrice 미포함 — 팀 합의
    public static String compute(List<CartItem> cartItems) {
        String serialized = cartItems.stream()
            .sorted(Comparator.comparing(item -> item.getEventId().toString()))
            .map(item -> item.getEventId() + ":" + item.getQuantity())
            .collect(Collectors.joining(","));

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(serialized.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 알고리즘을 찾을 수 없습니다.", e);
        }
    }
}
