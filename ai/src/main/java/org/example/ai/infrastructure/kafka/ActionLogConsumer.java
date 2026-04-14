package org.example.ai.infrastructure.kafka;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.ai.application.service.VectorService;
import org.example.ai.presentation.dto.req.ActionLogMessage;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ActionLogConsumer {

    private final VectorService vectorService;

    @KafkaListener(
        topics = "action.log",
        groupId = "ai.action-log",
        containerFactory = "actionLogKafkaListenerContainerFactory"
    )
    public void consume(ActionLogMessage message) {
        log.info("Action Log 수신 - actionType : {}, userId : {}", message.actionType(), message.userId());

        switch (message.actionType()) {
            case "PURCHASE"     -> vectorService.updatePreferenceVector(message.userId(), message.eventId());
            case "REFUND"       -> vectorService.updateRefund(message.userId(), message.eventId());
            case "CART_ADD"     -> vectorService.updateCartVector(message.userId(), message.eventId());
            case "CART_REMOVE"  -> vectorService.updateNegativeVector(message.userId(), message.eventId());
            case "DETAIL_VIEW", "DWELL_TIME", "VIEW" -> log.info("[ActionLog] Spring Batch 대상 - actionType: {}", message.actionType());
            default             -> log.warn("[ActionLog] 알 수 없는 actionType: {}", message.actionType());
        }
    }

}
