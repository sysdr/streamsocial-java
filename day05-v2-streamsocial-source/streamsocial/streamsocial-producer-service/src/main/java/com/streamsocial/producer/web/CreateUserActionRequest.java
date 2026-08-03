package com.streamsocial.producer.web;

import com.streamsocial.common.event.UserActionType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateUserActionRequest(
        @NotBlank String userId,
        @NotNull UserActionType actionType,
        String targetId
) {
}
