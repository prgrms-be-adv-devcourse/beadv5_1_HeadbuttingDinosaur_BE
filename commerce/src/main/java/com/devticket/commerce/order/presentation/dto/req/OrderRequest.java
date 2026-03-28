package com.devticket.commerce.order.presentation.dto.req;

import java.util.List;

public record OrderRequest(
    List<String> cartItemEventIds
) {

}

