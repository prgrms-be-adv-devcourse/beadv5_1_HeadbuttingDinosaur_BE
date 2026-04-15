package com.devticket.commerce;

import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableJpaAuditing
@EnableScheduling
@EnableSchedulerLock(defaultLockAtMostFor = "PT25S")
@SpringBootApplication
public class CommerceApplication {

    public static void main(String[] args) {
        SpringApplication.run(CommerceApplication.class, args);
    }

}
