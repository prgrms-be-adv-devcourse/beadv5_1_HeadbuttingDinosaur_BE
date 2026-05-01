package com.devticket.event.infrastructure.client;

import com.devticket.event.infrastructure.client.dto.TechStackItem;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Slf4j
@Component
@RequiredArgsConstructor
public class AdminClient {

    private final RestTemplate restTemplate;

    @Value("${service.admin.url}")
    private String adminServiceUrl;

    public List<TechStackItem> getTechStacks() {
        try {
            String url = adminServiceUrl + "/internal/admin/tech-stacks";
            TechStackItem[] items = restTemplate.getForObject(url, TechStackItem[].class);
            if (items == null) {
                return List.of();
            }
            return items != null ? List.of(items) : List.of();
        } catch (Exception e) {
            log.warn("[기술스택 조회 실패] Admin 서비스 호출 오류", e);
            return List.of();
        }
    }
}
