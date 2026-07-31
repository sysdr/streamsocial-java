package com.streamsocial.common.event;

/** Something the platform itself observed about its own operation, not a user action. */
public sealed interface SystemEvent extends DomainEvent
        permits ServiceHealthChanged, RebalanceTriggered {
}
