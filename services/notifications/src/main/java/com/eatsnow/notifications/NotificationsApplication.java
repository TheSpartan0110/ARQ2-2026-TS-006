package com.eatsnow.notifications;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = {"com.eatsnow.notifications", "com.eatsnow.common"})
@EnableScheduling // email dispatcher; the messaging module's outbox relay stays idle (Notifications publishes nothing)
public class NotificationsApplication {
    public static void main(String[] args) {
        SpringApplication.run(NotificationsApplication.class, args);
    }
}
