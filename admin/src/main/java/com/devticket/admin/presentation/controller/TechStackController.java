package com.devticket.admin.presentation.controller;

import com.devticket.admin.application.service.TechStackService;
import com.devticket.admin.presentation.dto.req.CreateTechStackRequest;
import com.devticket.admin.presentation.dto.req.UpdateTechStackRequest;
import com.devticket.admin.presentation.dto.res.CreateTechStackResponse;
import com.devticket.admin.presentation.dto.res.DeleteTechStackResponse;
import com.devticket.admin.presentation.dto.res.GetTechStackResponse;
import com.devticket.admin.presentation.dto.res.UpdateTechStackResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/techstacks")
@Tag(name = "TechStack", description = "TechStack 관리 API")
public class TechStackController {

    private final TechStackService techStackService;

    @PostMapping
    @Operation(summary = "TechStack 생성")
    public ResponseEntity<CreateTechStackResponse> createTechStack(
        @RequestBody @Valid CreateTechStackRequest request
    ) {
        return ResponseEntity.ok(techStackService.createTechStack(request));
    }

    @PutMapping("/{id}")
    @Operation(summary = "TechStack 수정")
    public ResponseEntity<UpdateTechStackResponse> updateTechStack(
        @PathVariable Long id,
        @RequestBody @Valid UpdateTechStackRequest request
    ) {
        return ResponseEntity.ok(techStackService.updateTechStack(id, request));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "TechStack 삭제")
    public ResponseEntity<DeleteTechStackResponse> deleteTechStack(
        @PathVariable Long id
    ) {
        return ResponseEntity.ok(techStackService.deleteTechStack(id));
    }

    @GetMapping
    @Operation(summary = "TechStack 전체 조회")
    public ResponseEntity<List<GetTechStackResponse>> getTechStacks(
    ) {
        return ResponseEntity.ok(techStackService.getTechStacks());
    }


}

