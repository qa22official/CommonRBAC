package com.example.rbac.auth.internal;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Publishes token-revocation domain events after commit. In the auth module the
 * version increment happens in its own transaction, so this is typically a
 * no-op wrapper, but it keeps the ordering rule explicit.
 */
@Component
public class AuthEvents {

    private final ApplicationEventPublisher publisher;

    public AuthEvents(ApplicationEventPublisher publisher) {
        this.publisher = publisher;
    }

    public void tokenRevoked(long userId, long tokenVersion) {
        Object event = new com.example.rbac.api.event.TokenRevokedEvent(userId, tokenVersion);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    publisher.publishEvent(event);
                }
            });
        } else {
            publisher.publishEvent(event);
        }
    }
}
