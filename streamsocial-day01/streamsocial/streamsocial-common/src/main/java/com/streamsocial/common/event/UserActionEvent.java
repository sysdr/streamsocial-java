package com.streamsocial.common.event;

/** Something a user directly did - authored, deleted, or changed something about themselves. */
public sealed interface UserActionEvent extends DomainEvent
        permits PostCreated, PostDeleted, ProfileUpdated {
}
