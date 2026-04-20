package com.devticket.commerce;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.kafka.autoconfigure.KafkaAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(properties = {
    "spring.kafka.bootstrap-servers=localhost:9092",
})
@ActiveProfiles("test")
@EnableAutoConfiguration(exclude = {
    KafkaAutoConfiguration.class
})
class CommerceApplicationTests {

    @Test
    void contextLoads() {
    }

}
