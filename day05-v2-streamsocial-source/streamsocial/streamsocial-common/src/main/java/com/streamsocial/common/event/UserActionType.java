package com.streamsocial.common.event;

/**
 * The five user-initiated action types StreamSocial tracks at launch.
 * Each one becomes a distinct {@code eventType()} value carried on
 * {@link UserActionEvent}.
 */
public enum UserActionType {
    POST_CREATED,
    POST_DELETED,
    USER_FOLLOWED,
    USER_UNFOLLOWED,
    PROFILE_UPDATED
}
