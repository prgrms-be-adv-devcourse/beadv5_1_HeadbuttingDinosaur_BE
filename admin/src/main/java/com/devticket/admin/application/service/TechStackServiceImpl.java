package com.devticket.admin.application.service;

import com.devticket.admin.application.event.TechStackCreatedEvent;
import com.devticket.admin.application.event.TechStackDeletedEvent;
import com.devticket.admin.application.event.TechStackUpdatedEvent;
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
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@RequiredArgsConstructor
@Service
public class TechStackServiceImpl implements TechStackService{

    private final TechStackRepository  techStackRepository;
    private final ApplicationEventPublisher eventPublisher;
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

        // 2. name -> (OpenAI) -> embedding
        float[] embedding = openAiEmbeddingClient.embed(name);

        // 3. DB 커밋 후 생성 이벤트 발행(이벤트 발행 -> ES 저장 실행)
        eventPublisher.publishEvent(new TechStackCreatedEvent(saved.getId(), name, embedding));

        return CreateTechStackResponse.from(saved);
    }

    // =============== 3. TechStack 수정 =============== //
    @Transactional
    public UpdateTechStackResponse updateTechStack(Long id, UpdateTechStackRequest request) {
        TechStack techStack = techStackRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 TechStack: " + id));

        // 1. RDB 업데이트
        techStack.updateName(request.name());

        // 2. name 재임베딩
        float[] embedding = openAiEmbeddingClient.embed(request.name());

        // 3. DB 커밋 후 수정 이벤트 발행(이벤트 발행 -> ES 저장 실행)
        eventPublisher.publishEvent(new TechStackUpdatedEvent(id, request.name(), embedding));

        return UpdateTechStackResponse.from(techStack);
    }

    // =============== 4. TechStack 삭제 =============== //
    @Transactional
    public DeleteTechStackResponse deleteTechStack(Long id) {
        techStackRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 TechStack: " + id));

        // 1. RDB 삭제
        techStackRepository.deleteById(id);
        
        // 2. DB 커밋 후 삭제 이벤트 발행(이벤트 발행 -> ES 삭제 실행)
        eventPublisher.publishEvent(new TechStackDeletedEvent(id));

        return DeleteTechStackResponse.of(id);
    }

}
