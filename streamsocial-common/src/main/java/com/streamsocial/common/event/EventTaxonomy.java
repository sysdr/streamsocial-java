package com.streamsocial.common.event;

import java.util.List;

/**
 * A single, queryable catalog of every event type StreamSocial defines.
 *
 * <p>This is deliberately not clever - it is a flat list, built once, at
 * class-load time. Day 26 will replace the {@code description} field with
 * a real JSON Schema reference and Day 58 will turn this whole class into
 * a governance report generator, but the shape - one row per event type,
 * grouped by category - stays the same for the rest of the course.
 */
public final class EventTaxonomy {

    /** One row in the catalog. */
    public record CatalogEntry(String category, String eventType, String description) {
    }

    public static final List<CatalogEntry> ALL = List.of(
            new CatalogEntry("UserActionEvent", UserActionType.POST_CREATED.name(),
                    "A user published a new post."),
            new CatalogEntry("UserActionEvent", UserActionType.POST_DELETED.name(),
                    "A user removed a post they previously published."),
            new CatalogEntry("UserActionEvent", UserActionType.USER_FOLLOWED.name(),
                    "A user started following another user."),
            new CatalogEntry("UserActionEvent", UserActionType.USER_UNFOLLOWED.name(),
                    "A user stopped following another user."),
            new CatalogEntry("UserActionEvent", UserActionType.PROFILE_UPDATED.name(),
                    "A user changed profile fields (bio, avatar, display name)."),

            new CatalogEntry("ContentInteractionEvent", InteractionType.CONTENT_LIKED.name(),
                    "A user liked a piece of content."),
            new CatalogEntry("ContentInteractionEvent", InteractionType.CONTENT_COMMENTED.name(),
                    "A user commented on a piece of content."),
            new CatalogEntry("ContentInteractionEvent", InteractionType.CONTENT_SHARED.name(),
                    "A user shared a piece of content to their own timeline."),

            new CatalogEntry("SystemEvent", SystemEventType.CONTENT_MODERATION_FLAGGED.name(),
                    "StreamSocial's moderation pipeline flagged content for review."),
            new CatalogEntry("SystemEvent", SystemEventType.RECOMMENDATION_MODEL_UPDATED.name(),
                    "A new recommendation model version was promoted to production.")
    );

    private EventTaxonomy() {
        // catalog holder, not instantiable
    }
}
