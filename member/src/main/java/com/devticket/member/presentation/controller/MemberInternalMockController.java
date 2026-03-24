package com.devticket.member.presentation.controller;

import com.devticket.member.presentation.dto.response.MemberInternalResponse;
import com.devticket.member.presentation.dto.response.MemberRoleResponse;
import com.devticket.member.presentation.dto.response.MemberStatusResponse;
import com.devticket.member.presentation.dto.response.SellerInfoResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/members")
public class MemberInternalMockController {

    @GetMapping("/{userId}")
    public MemberInternalResponse getMember(@PathVariable Long userId) {
        return new MemberInternalResponse(
            42L,
            "user@example.com",
            "USER",
            "ACTIVE",
            "LOCAL"
        );
    }

    @GetMapping("/{userId}/status")
    public MemberStatusResponse getMemberStatus(@PathVariable Long userId) {
        return new MemberStatusResponse(
            42L,
            "ACTIVE"
        );
    }

    @GetMapping("/{userId}/role")
    public MemberRoleResponse getMemberRole(@PathVariable Long userId) {
        return new MemberRoleResponse(
            42L,
            "SELLER"
        );
    }

    @GetMapping("/{userId}/seller-info")
    public SellerInfoResponse getSellerInfo(@PathVariable Long userId) {
        return new SellerInfoResponse(
            42L,
            "카카오뱅크",
            "3333-01-1234567",
            "김개발"
        );
    }
}
