package com.devticket.member.infrastructure.external.client;

import com.devticket.member.presentation.dto.internal.response.InternalAdminTechStackResponse;

public interface AdminInternalClient {

    InternalAdminTechStackResponse getTechStacks();

}
