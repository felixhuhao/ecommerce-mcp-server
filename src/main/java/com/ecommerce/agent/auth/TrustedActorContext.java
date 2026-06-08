package com.ecommerce.agent.auth;

import java.util.function.Supplier;

import org.springframework.stereotype.Component;

@Component
public class TrustedActorContext {

    private static final ThreadLocal<TrustedActor> CURRENT = new ThreadLocal<>();

    public TrustedActor requireCurrentActor() {
        TrustedActor actor = CURRENT.get();
        if (actor == null) {
            throw new IllegalStateException("trusted actor is not available");
        }
        return actor;
    }

    public void setCurrentActor(TrustedActor actor) {
        CURRENT.set(actor);
    }

    public void clear() {
        CURRENT.remove();
    }

    public <T> T runAs(Long userId, String sessionId, Supplier<T> action) {
        TrustedActor previous = CURRENT.get();
        CURRENT.set(new TrustedActor(userId, sessionId));
        try {
            return action.get();
        } finally {
            if (previous == null) {
                CURRENT.remove();
            } else {
                CURRENT.set(previous);
            }
        }
    }
}
