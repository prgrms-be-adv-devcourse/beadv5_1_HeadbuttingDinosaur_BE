package com.devticket.admin.presentation.dto.res;

import java.util.List;

public record AdminUserListResponse(
    List<UserListItem> content,
    int page,
    int size,
    long totalElements,
    int totalPages
) {}
