package com.devticket.event.common.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "openai.embedding")
public class OpenAiProperties {

    private boolean enabled;
    private String apiKey;
    private String model;
}
