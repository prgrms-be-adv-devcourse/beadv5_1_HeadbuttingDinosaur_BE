package com.devticket.event.presentation.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.devticket.event.application.EventService;
import com.devticket.event.domain.model.EventCategory;
import com.devticket.event.presentation.dto.SellerEventCreateRequest;
import tools.jackson.databind.json.JsonMapper;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(EventController.class)
class EventControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JsonMapper objectMapper;

    @MockitoBean
    private EventService eventService;

    @Test
    @DisplayName("POST /api/v1/events - 이벤트 생성 API 호출 성공")
    void createEventApi_Success() throws Exception {
        // given
        Long sellerId = 1L;
        UUID expectedEventId = UUID.randomUUID();
        LocalDateTime now = LocalDateTime.now();

        SellerEventCreateRequest request = new SellerEventCreateRequest(
            "테스트 밋업", "설명", "강남역",
            now.plusDays(15), now.plusDays(4), now.plusDays(10),
            50000, 100, 4, EventCategory.MEETUP,
            List.of(UUID.randomUUID()), List.of("url1")
        );

        when(eventService.createEvent(eq(sellerId), any(SellerEventCreateRequest.class)))
            .thenReturn(expectedEventId);

        // when & then
        mockMvc.perform(post("/api/v1/events")
                .header("X-User-Id", String.valueOf(sellerId))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andDo(print())
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data.eventId").value(expectedEventId.toString()));
    }
}