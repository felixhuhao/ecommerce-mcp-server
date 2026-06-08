package com.ecommerce.agent.auth;

public record TrustedActor(Long userId, String sessionId) {
    public TrustedActor {
        if (userId == null || userId <= 0) {
            throw new IllegalArgumentException("userId must be positive");
        }
        if (sessionId == null || sessionId.isBlank()) {
            throw new IllegalArgumentException("sessionId must not be blank");
        }
    }
}
