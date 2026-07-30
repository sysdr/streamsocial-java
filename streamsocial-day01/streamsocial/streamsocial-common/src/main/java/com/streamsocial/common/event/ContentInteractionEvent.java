package com.streamsocial.common.event;

/** Something a user did to someone else's content - the engagement signals StreamSocial ranks on. */
public sealed interface ContentInteractionEvent extends DomainEvent
        permits PostLiked, PostShared, PostCommented {
}
