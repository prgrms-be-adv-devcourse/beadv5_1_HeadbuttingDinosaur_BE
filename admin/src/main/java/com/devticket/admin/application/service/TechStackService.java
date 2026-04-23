package com.devticket.admin.application.service;

import com.devticket.admin.domain.model.TechStackDocument;
import com.devticket.admin.presentation.dto.req.CreateTechStackRequest;
import com.devticket.admin.presentation.dto.req.DeleteTechStackRequest;
import com.devticket.admin.presentation.dto.req.UpdateTechStackRequest;
import com.devticket.admin.presentation.dto.res.CreateTechStackResponse;
import com.devticket.admin.presentation.dto.res.DeleteTechStackResponse;
import com.devticket.admin.presentation.dto.res.GetTechStackResponse;
import com.devticket.admin.presentation.dto.res.UpdateTechStackResponse;
import java.util.List;

public interface TechStackService {

    List<GetTechStackResponse> getTechStacks();

    CreateTechStackResponse createTechStack(CreateTechStackRequest request);

    UpdateTechStackResponse updateTechStack(Long id, UpdateTechStackRequest request);

    DeleteTechStackResponse deleteTechStack(Long id);

    void reindexEmptyEmbeddings();



}
