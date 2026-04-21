package com.devticket.member.presentation.controller;

import com.devticket.member.application.UserService;
import com.devticket.member.infrastructure.external.dto.res.InternalAdminTechStackResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "TechStack", description = "기술 스택 API")
@RestController
@RequestMapping("/api/tech-stacks")
@RequiredArgsConstructor
public class TechStackController {

    private final UserService userService;

    @Operation(summary = "기술 스택 목록 조회", description = "회원가입·프로필·이벤트 생성 시 기술스택 선택용")
    @GetMapping
    public ResponseEntity<InternalAdminTechStackResponse> getTechStacks() {
        return ResponseEntity.ok(userService.getAllTechStacks());
    }
}
