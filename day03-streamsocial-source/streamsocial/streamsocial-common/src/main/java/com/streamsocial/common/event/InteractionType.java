package com.streamsocial.common.event;

/**
 * The three ways one user can react to another user's content.
 * Carried on {@link ContentInteractionEvent}.
 */
public enum InteractionType {
    CONTENT_LIKED,
    CONTENT_COMMENTED,
    CONTENT_SHARED
}
