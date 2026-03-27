package com.devticket.member.application;

import com.devticket.member.presentation.dto.request.SellerApplicationRequest;
import com.devticket.member.presentation.dto.response.SellerApplicationResponse;
import com.devticket.member.presentation.dto.response.SellerApplicationStatusResponse;
import java.time.LocalDateTime;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class SellerApplicationService {

    public SellerApplicationResponse apply(UUID userId, SellerApplicationRequest request) {
        // TODO: Phase 4에서 구현
        return new SellerApplicationResponse(UUID.randomUUID());
    }

    public SellerApplicationStatusResponse getMyApplication(UUID userId) {
        // TODO: Phase 4에서 구현
        return new SellerApplicationStatusResponse("PENDING", LocalDateTime.now());
    }
}
