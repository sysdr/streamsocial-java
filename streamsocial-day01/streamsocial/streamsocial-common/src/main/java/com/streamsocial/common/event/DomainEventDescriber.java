package com.streamsocial.common.event;

/**
 * Deliberately written with instanceof pattern matching (stable since Java 16) rather than
 * switch pattern matching over the sealed hierarchy (still preview in Java 17, finalized in 21).
 * The final else branch is what the Java 21 track collapses away once switch can prove
 * exhaustiveness over a sealed type at compile time.
 */
public final class DomainEventDescriber {

    private DomainEventDescriber() {
    }

    public static String describe(DomainEvent event) {
        if (event instanceof PostCreated e) {
            return "user " + e.userId() + " created post " + e.postId();
        } else if (event instanceof PostDeleted e) {
            return "user " + e.userId() + " deleted post " + e.postId();
        } else if (event instanceof ProfileUpdated e) {
            return "user " + e.userId() + " updated " + e.fieldChanged();
        } else if (event instanceof PostLiked e) {
            return "user " + e.userId() + " liked post " + e.postId();
        } else if (event instanceof PostShared e) {
            return "user " + e.userId() + " shared post " + e.postId() + " to " + e.sharedToUserId();
        } else if (event instanceof PostCommented e) {
            return "user " + e.userId() + " commented on post " + e.postId();
        } else if (event instanceof ServiceHealthChanged e) {
            return "service " + e.serviceName() + " is now " + e.status();
        } else if (event instanceof RebalanceTriggered e) {
            return "consumer group " + e.consumerGroupId() + " reassigned " + e.partitionsReassigned() + " partitions";
        }
        throw new IllegalStateException("Unhandled DomainEvent implementation: " + event.getClass());
    }
}
