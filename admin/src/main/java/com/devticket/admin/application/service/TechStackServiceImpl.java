package com.devticket.admin.application.service;

import com.devticket.admin.domain.model.TechStack;
import com.devticket.admin.domain.repository.TechStackRepository;
import com.devticket.admin.infrastructure.external.client.OpenAiEmbeddingClient;
import com.devticket.admin.infrastructure.persistence.repository.TechStackEsRepository;
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

    private final TechStackEsRepository  techStackEsRepository;
    private final TechStackRepository  techStackRepository;
    private final OpenAiEmbeddingClient openAiEmbeddingClient;

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

        // 1. RDB 저장
        TechStack saved = techStackRepository.save(TechStack.of(null, name));

        // 2. ES 저장
        float[] embedding = openAiEmbeddingClient.embed(name);
        techStackEsRepository.save(saved.getId(),name, embedding);

        return CreateTechStackResponse.from(saved);
    }

    // =============== 3. TechStack 수정 =============== //
    @Transactional
    public UpdateTechStackResponse updateTechStack(Long id, UpdateTechStackRequest request) {
        TechStack techStack = techStackRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 TechStack: " + id));

        // 1. RDB 업데이트
        techStack.updateName(request.name());

        // 2. ES  업데이트
        float[] embedding = openAiEmbeddingClient.embed(request.name());
        techStackEsRepository.update(id, request.name(), embedding);

        return UpdateTechStackResponse.from(techStack);
    }

    // =============== 4. TechStack 삭제 =============== //
    @Transactional
    public DeleteTechStackResponse deleteTechStack(Long id) {
        techStackRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 TechStack: " + id));

        // 1. RDB 삭제
        techStackRepository.deleteById(id);
        // 2. ES 삭제
        techStackEsRepository.delete(id);

        return DeleteTechStackResponse.of(id);
    }

}
