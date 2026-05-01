package com.devticket.admin.presentation.dto.res;

public record DeleteTechStackResponse(
    Long id
) {
    public static DeleteTechStackResponse of(Long id) {
        return new DeleteTechStackResponse(id);
    }
}
