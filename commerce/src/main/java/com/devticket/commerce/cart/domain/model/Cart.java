package com.devticket.commerce.cart.domain.model;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.Table;
import java.util.UUID;

//장바구니 소유자
// JPA 엔티티를 위한 기본생성자 필수
//@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "\"cart\"", schema = "public")
public class Cart {

    @Schema(description = "장바구니 소유자 ID : User PK")
    private UUID userId;

    //---JPA엔티티를 위한 기본생성자---------------------
    private Cart(UUID userId) {

    }

    //----정적팩토리 메서드 ----------------------------
    //create
//    public static Cart create(UUID userId) {
//        Cart newCart = new Cart();
//        newCart.userId = userId;
//        return newCart;
//    }
    //---도메인 비즈니스 메서드--------------------------

    private void validateUserId(UUID userId) {
        if (userId == null) {
            throw new IllegalArgumentException("장바구니 소유자 ID는 필수입니다.");
        }
    }


}
