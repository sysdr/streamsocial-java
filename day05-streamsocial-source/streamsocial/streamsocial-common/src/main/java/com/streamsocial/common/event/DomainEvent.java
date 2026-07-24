package com.streamsocial.common.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Root of the StreamSocial event taxonomy.
 *
 * <p>Every fact StreamSocial will ever produce onto a Kafka topic - starting
 * with Day 4's producer - implements this interface. Sealing it to exactly
 * three permitted subtypes is deliberate: it forces every future event to be
 * classified into one of the three categories a social platform actually
 * generates, instead of letting a fourth ad-hoc "misc" event type creep in
 * six months into the project.
 *
 * <ul>
 *   <li>{@link UserActionEvent} - something a user deliberately did
 *       (posted, followed, updated a profile).</li>
 *   <li>{@link ContentInteractionEvent} - a user reacting to someone else's
 *       content (like, comment, share).</li>
 *   <li>{@link SystemEvent} - something StreamSocial itself decided,
 *       independent of a single user action (a moderation flag, a model
 *       refresh).</li>
 * </ul>
 *
 * <p>The compiler enforces this taxonomy: a {@code switch} over
 * {@code DomainEvent} with all three branches covered needs no
 * {@code default} case, and the build fails the moment a new subtype is
 * added anywhere and a switch isn't updated to handle it.
 */
public sealed interface DomainEvent
        permits UserActionEvent, ContentInteractionEvent, SystemEvent {

    /**
     * Globally unique identifier for this exact occurrence. Later lessons
     * use this as the Kafka message key's tiebreaker and as the idempotency
     * token for Day 13's idempotent producer work.
     */
    UUID eventId();

    /** Wall-clock instant the event happened, not when it was published. */
    Instant occurredAt();

    /**
     * Short, stable, upper-snake-case type name (e.g. {@code POST_CREATED}).
     * This is what Day 26's JSON Schema catalog and Day 27's Avro schema
     * will key on.
     */
    String eventType();
}
