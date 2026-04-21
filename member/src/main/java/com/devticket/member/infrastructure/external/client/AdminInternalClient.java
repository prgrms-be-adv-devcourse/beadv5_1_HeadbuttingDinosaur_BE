package com.devticket.member.infrastructure.external.client;

import com.devticket.member.infrastructure.external.dto.res.InternalAdminTechStackResponse;

public interface AdminInternalClient {

    InternalAdminTechStackResponse getTechStacks();

}
