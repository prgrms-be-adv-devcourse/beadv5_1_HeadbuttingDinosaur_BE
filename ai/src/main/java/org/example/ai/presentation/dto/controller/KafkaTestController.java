package org.example.ai.presentation.dto.controller;

import lombok.RequiredArgsConstructor;

import org.example.ai.presentation.dto.req.ActionLogMessage;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/test")
@RequiredArgsConstructor
public class KafkaTestController {

    private final KafkaTemplate<String, ActionLogMessage> kafkaTemplate;

    @PostMapping("/kafka")
    public String send(@RequestBody ActionLogMessage message) {
        kafkaTemplate.send("action.log", message);
        return "발행 완료";
    }
}
