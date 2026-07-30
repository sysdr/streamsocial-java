package com.streamsocial.common.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Root of StreamSocial's event taxonomy. Every fact that has ever happened on the
 * platform is one of exactly these three categories - the compiler enforces it.
 */
public sealed interface DomainEvent permits UserActionEvent, ContentInteractionEvent, SystemEvent {

    UUID eventId();

    Instant occurredAt();
}
