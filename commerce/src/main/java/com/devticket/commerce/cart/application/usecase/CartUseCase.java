package com.devticket.commerce.cart.application.usecase;

import com.devticket.commerce.cart.presentation.dto.req.CartItemRequest;
import com.devticket.commerce.cart.presentation.dto.res.CartItemResponse;
import java.util.UUID;

public interface CartUseCase {

    //사용자의 장바구니 유무 확인
<<<<<<< Updated upstream
    boolean findByUserId(Long userId);
=======
    boolean finByUserId(UUID userId);
>>>>>>> Stashed changes

    //장바구니 생성
    CartItemResponse save(UUID userId, CartItemRequest request);


}
