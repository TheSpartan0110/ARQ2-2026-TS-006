package com.eatsnow.restaurants;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = {"com.eatsnow.restaurants", "com.eatsnow.common"})
@EnableScheduling // the outbox relay of the messaging module is a @Scheduled bean
public class RestaurantsApplication {
    public static void main(String[] args) {
        SpringApplication.run(RestaurantsApplication.class, args);
    }
}
