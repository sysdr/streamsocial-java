package com.streamsocial.producer.web;

import com.streamsocial.common.event.UserActionEvent;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * The first real producer in this course. One endpoint, one shared
 * {@link KafkaTemplate}, publishing onto {@code user-actions} (Day 3)
 * with the exact {@link UserActionEvent} shape defined Day 1.
 *
 * <p>Blocks on the send's future with a short timeout before responding.
 * The decoupling event-driven architecture buys StreamSocial is between
 * this service and whatever consumes {@code user-actions} downstream
 * (Day 5 onward) - not between this endpoint and the one broker it's
 * directly talking to. A caller getting back a 202 with a real
 * {@code partition} and {@code offset} - read from the producer's
 * actual ack, not invented - knows the event is durably on the log.
 */
@RestController
@RequestMapping("/api/v1/actions")
public class UserActionController {

    private static final Logger log = LoggerFactory.getLogger(UserActionController.class);
    private static final String TOPIC = "user-actions";
    private static final long SEND_TIMEOUT_SECONDS = 2;

    private final KafkaTemplate<String, UserActionEvent> kafkaTemplate;

    public UserActionController(KafkaTemplate<String, UserActionEvent> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    @PostMapping
    public ResponseEntity<?> createAction(@Valid @RequestBody CreateUserActionRequest request) {
        UserActionEvent event = new UserActionEvent(
                UUID.randomUUID(), Instant.now(), request.userId(), request.actionType(), request.targetId());

        try {
            SendResult<String, UserActionEvent> result =
                    kafkaTemplate.send(TOPIC, event.userId(), event)
                            .get(SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS);

            RecordMetadata metadata = result.getRecordMetadata();
            UserActionResponse response = new UserActionResponse(
                    event.eventId(), metadata.topic(), metadata.partition(), metadata.offset());

            return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);

        } catch (TimeoutException e) {
            log.warn("Timed out waiting for broker ack for event {}", event.eventId());
            return serviceUnavailable(event.eventId());
        } catch (ExecutionException e) {
            log.error("Broker rejected event {}", event.eventId(), e.getCause());
            return serviceUnavailable(event.eventId());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return serviceUnavailable(event.eventId());
        }
    }

    private ResponseEntity<Map<String, Object>> serviceUnavailable(UUID eventId) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of("error", "failed to publish event", "eventId", eventId));
    }
}
