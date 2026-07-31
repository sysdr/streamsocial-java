package com.streamsocial.dashboard;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class StreamsocialDashboardApplication {

    public static void main(String[] args) {
        SpringApplication.run(StreamsocialDashboardApplication.class, args);
    }
}
