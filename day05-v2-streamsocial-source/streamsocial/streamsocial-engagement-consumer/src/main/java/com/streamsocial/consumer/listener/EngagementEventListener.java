package com.streamsocial.consumer.listener;

import com.streamsocial.common.event.ContentInteractionEvent;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Day 5's actual deliverable: read {@code content-interactions}, validate
 * what came off the wire, log it in a structured, greppable shape.
 *
 * <p>{@code processed} exists purely so tests have something concrete to
 * assert against without parsing log output - the listener's real
 * behavior (validating, logging, occasionally throwing) doesn't change
 * because it's being observed.
 */
@Component
public class EngagementEventListener {

    private static final Logger log = LoggerFactory.getLogger(EngagementEventListener.class);

    /**
     * A deliberate failure hook for today's error-handling demo only -
     * not a pattern for real listener logic. Simulates "some downstream
     * dependency this listener needs is temporarily down."
     */
    static final String POISON_CONTENT_ID = "post-BOOM";

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
    private final List<ContentInteractionEvent> processed = new CopyOnWriteArrayList<>();

    @KafkaListener(
            topics = "content-interactions",
            groupId = "engagement-consumer",
            containerFactory = "engagementListenerContainerFactory")
    public void onInteraction(ConsumerRecord<String, ContentInteractionEvent> record) {
        ContentInteractionEvent event = record.value();

        Set<ConstraintViolation<ContentInteractionEvent>> violations = validator.validate(event);
        if (!violations.isEmpty()) {
            throw new IllegalArgumentException(
                    "invalid ContentInteractionEvent at offset " + record.offset() + ": " + violations);
        }

        if (POISON_CONTENT_ID.equals(event.contentId())) {
            throw new IllegalStateException("simulated processing failure for " + POISON_CONTENT_ID);
        }

        log.info("STRUCTURED_EVENT event=engagement-processed interactionType={} userId={} contentId={} partition={} offset={}",
                event.interactionType(), event.userId(), event.contentId(), record.partition(), record.offset());

        processed.add(event);
    }

    public List<ContentInteractionEvent> getProcessed() {
        return processed;
    }
}
