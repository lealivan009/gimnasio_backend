package com.authbase.api.dto;

public record RegisterResponse(
        String message,
        UserResponse user
) {
}
