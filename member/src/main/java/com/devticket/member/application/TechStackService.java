package com.devticket.member.application;

import com.devticket.member.presentation.domain.model.TechStack;
import com.devticket.member.presentation.domain.repository.TechStackRepository;
import com.devticket.member.presentation.dto.request.CreateTechStackRequest;
import com.devticket.member.presentation.dto.request.DeleteTechStackRequest;
import com.devticket.member.presentation.dto.request.UpdateTechStackRequest;
import com.devticket.member.presentation.dto.response.CreateTechStackResponse;
import com.devticket.member.presentation.dto.response.DeleteTechStackResponse;
import com.devticket.member.presentation.dto.response.UpdateTechStackResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@RequiredArgsConstructor
@Service
public class TechStackService {

    private final TechStackRepository techStackRepository;


    // =============== 1. TechStack 생성 =============== //
    public CreateTechStackResponse createTechStack(CreateTechStackRequest request){
        String name = request.name();

        if(techStackRepository.existsByName(name)){
            throw new IllegalArgumentException("이미 존재하는 TechStack: " + name);
        }

        // 1. Tech_Stack RDB 저장
        TechStack saved = techStackRepository.save(TechStack.of(null,name));
        // 2. Tech_Stack ES 저장

        return CreateTechStackResponse.from(saved);
    }

    // =============== 2. TechStack 수정 =============== //
    UpdateTechStackResponse updateTechStack(UpdateTechStackRequest request){

        TechStack techStack = techStackRepository.findById(request.id())
            .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 TechStack: " + request.id()));

        // 1. Tech_Stack RDB 수정
        techStack.updateName(request.name());

        return UpdateTechStackResponse.from(techStack);
    }

    // =============== 3. TechStack 삭제 =============== //
    DeleteTechStackResponse deleteTechStack(DeleteTechStackRequest request){
        Long id = request.id();
        techStackRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 TechStack: " + id));
        
        // 1. Tech_Stack RDB 삭제
        techStackRepository.deleteById(id);

        return DeleteTechStackResponse.of(id);
    }

}
