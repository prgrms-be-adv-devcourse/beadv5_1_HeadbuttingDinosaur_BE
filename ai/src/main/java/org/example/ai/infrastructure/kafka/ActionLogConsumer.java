package org.example.ai.infrastructure.kafka;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.ai.presentation.dto.req.ActionLogMessage;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ActionLogConsumer {

    @KafkaListener(
        topics = "action.log",
        groupId = "ai.action-log",
        containerFactory = "actionLogKafkaListenerContainerFactory"
    )
    public void consume(ActionLogMessage message) {
        log.info("Action Log 수신 - actionType : {}, userId : {}", message.actionType(), message.userId());

        switch (message.actionType()) {
            case "PURCHASE"     -> log.info("preference_vector 갱신 예정 - eventId: {}", message.eventId());
            case "CART_ADD"     -> log.info("cart_vector 갱신 예정 - eventId: {}", message.eventId());
            case "CART_REMOVE"  -> log.info("negative_vector 갱신 예정 - eventId: {}", message.eventId());
            case "REFUND"       -> log.info("negative_vector 갱신 + preference_vector 역보정 예정 - eventId: {}", message.eventId());
            case "DETAIL_VIEW"  -> log.info("recent_vector 갱신 예정 - eventId: {}", message.eventId());
            case "DWELL_TIME"   -> log.info("recent_vector 갱신 예정 - eventId: {}, dwellTime: {}", message.eventId(), message.dwellTimeSeconds());
            case "VIEW"         -> log.info("recent_vector 갱신 예정 - eventIds: {}", message.eventIds());
            default             -> log.warn("알 수 없는 actionType: {}", message.actionType());
        }
    }

}
