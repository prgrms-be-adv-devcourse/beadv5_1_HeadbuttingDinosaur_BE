package com.devticket.admin.application.service;

import com.devticket.admin.domain.model.TechStack;
import com.devticket.admin.domain.repository.TechStackRepository;
import com.devticket.admin.presentation.dto.req.CreateTechStackRequest;
import com.devticket.admin.presentation.dto.req.DeleteTechStackRequest;
import com.devticket.admin.presentation.dto.req.UpdateTechStackRequest;
import com.devticket.admin.presentation.dto.res.CreateTechStackResponse;
import com.devticket.admin.presentation.dto.res.DeleteTechStackResponse;
import com.devticket.admin.presentation.dto.res.GetTechStackResponse;
import com.devticket.admin.presentation.dto.res.UpdateTechStackResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@RequiredArgsConstructor
@Service
public class TechStackServiceImpl implements TechStackService{

    private final TechStackRepository techStackRepository;

    // =============== 1. TechStack 조회 =============== //
    @Transactional(readOnly = true)
    public List<GetTechStackResponse> getTechStacks() {
        return techStackRepository.getTechStacks().stream()
            .map(GetTechStackResponse::from)
            .toList();
    }

    // =============== 2. TechStack 생성 =============== //
    @Transactional
    public CreateTechStackResponse createTechStack(CreateTechStackRequest request) {
        String name = request.name();

        if (techStackRepository.existsByName(name)) {
            throw new IllegalArgumentException("이미 존재하는 TechStack: " + name);
        }

        TechStack saved = techStackRepository.save(TechStack.of(null, name));

        return CreateTechStackResponse.from(saved);
    }

    // =============== 3. TechStack 수정 =============== //
    @Transactional
    public UpdateTechStackResponse updateTechStack(Long id, UpdateTechStackRequest request) {
        TechStack techStack = techStackRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 TechStack: " + id));

        techStack.updateName(request.name());

        return UpdateTechStackResponse.from(techStack);
    }

    // =============== 4. TechStack 삭제 =============== //
    @Transactional
    public DeleteTechStackResponse deleteTechStack(Long id) {
        techStackRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 TechStack: " + id));

        techStackRepository.deleteById(id);

        return DeleteTechStackResponse.of(id);
    }

}
