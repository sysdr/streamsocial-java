package com.streamsocial.consumer;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * No web server here - this is a worker process. The JVM stays alive
 * because the Kafka listener container's poll-loop threads are
 * non-daemon by default, not because of any web starter.
 */
@SpringBootApplication
public class StreamSocialEngagementConsumerApplication {

    public static void main(String[] args) {
        SpringApplication.run(StreamSocialEngagementConsumerApplication.class, args);
    }
}
