package com.devticket.admin.application.service;

import com.devticket.admin.presentation.dto.res.SellerApplicationListResponse;
import java.util.List;
import java.util.UUID;

public interface AdminSellerService {

    List<SellerApplicationListResponse> getSellerApplicationList();

    void decideApplication(UUID adminId, UUID applicationId, String decision);

}
