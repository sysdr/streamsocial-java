package com.streamsocial.common.event;

/**
 * The two system-initiated event types StreamSocial tracks at launch.
 * Neither of these originates from a single user's direct action - they
 * come from StreamSocial's own backend processes.
 */
public enum SystemEventType {
    CONTENT_MODERATION_FLAGGED,
    RECOMMENDATION_MODEL_UPDATED
}
