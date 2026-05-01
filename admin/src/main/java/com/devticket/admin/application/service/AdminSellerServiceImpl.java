package com.devticket.admin.application.service;

import com.devticket.admin.infrastructure.external.client.MemberInternalClient;
import com.devticket.admin.infrastructure.external.dto.req.InternalDecideSellerApplicationRequest;
import com.devticket.admin.presentation.dto.req.AdminDecideSellerApplicationRequest;
import com.devticket.admin.presentation.dto.res.SellerApplicationListResponse;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AdminSellerServiceImpl implements AdminSellerService{

    private final MemberInternalClient memberInternalClient;

    @Override
    public List<SellerApplicationListResponse> getSellerApplicationList() {
        return memberInternalClient.getSellerApplications().stream()
            .map(app -> new SellerApplicationListResponse(
                app.sellerApplicationId(),
                app.userId(),
                app.bankName(),
                app.accountNumber(),
                app.accountHolder(),
                app.status(),
                app.createdAt()
            )).toList();
    }

    @Override
    public void decideApplication(UUID adminId, UUID applicationId, AdminDecideSellerApplicationRequest request) {
        memberInternalClient.decideSellerApplication(applicationId,
            new InternalDecideSellerApplicationRequest(request.decision()));
    }
}
