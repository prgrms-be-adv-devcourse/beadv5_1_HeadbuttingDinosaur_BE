package com.devticket.admin.presentation.controller;

import com.devticket.admin.application.service.TechStackService;
import com.devticket.admin.presentation.dto.res.GetTechStackResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@Tag(name = "Internal TechStack", description = "TechStack Internal api")
public class InternalTechStackController {

    private final TechStackService techStackService;

    @GetMapping("/internal/admin/tech-stacks")
    @Operation(summary = "TechStack 전체 조회 (Internal)")
    public ResponseEntity<List<GetTechStackResponse>> getTechStacks() {
        return ResponseEntity.ok(techStackService.getTechStacks());
    }

}
