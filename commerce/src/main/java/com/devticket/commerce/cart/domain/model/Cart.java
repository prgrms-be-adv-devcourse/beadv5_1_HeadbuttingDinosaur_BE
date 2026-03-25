package com.devticket.commerce.cart.domain.model;

import com.devticket.commerce.common.entity.BaseEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

//장바구니 소유자
@Entity
@Builder
@Getter
@Table(name = "\"cart\"", schema = "commerce")
// JPA 엔티티를 위한 기본생성자 필수
@NoArgsConstructor(access = AccessLevel.PROTECTED)
// 전체 생성자는 내부에서만 사용
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class Cart extends BaseEntity {

    @Id
    @Schema(description = "장바구니 PK")
    private UUID id;

    @Column(name = "user_id", unique = true, nullable = false)
    @Schema(description = "장바구니 소유자 ID : User PK")
    private UUID userId;


    //----정적팩토리 메서드 ----------------------------
    //create
    public static Cart create(UUID userId) {
        return Cart.builder()
            .id(UUID.randomUUID())
            .userId(userId)
            .build();
    }

    //---도메인 비즈니스 메서드--------------------------

}