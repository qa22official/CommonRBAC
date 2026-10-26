package com.example.rbac.core.internal.snapshot;

import com.example.rbac.api.identity.IdentitySnapshot;
import com.example.rbac.api.identity.IdentitySnapshotSource;
import com.example.rbac.core.internal.domain.UserEntity;
import com.example.rbac.core.internal.mapper.UserMapper;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Optional;

/**
 * Single-row identity lookup. A missing or logically deleted user is represented
 * as {@link Optional#empty()}; a disabled user is returned with
 * {@code enabled = false} so the guard can answer 40101 rather than 40100.
 */
@Component
public class IdentitySnapshotSourceImpl implements IdentitySnapshotSource {

    private final UserMapper userMapper;

    public IdentitySnapshotSourceImpl(UserMapper userMapper) {
        this.userMapper = userMapper;
    }

    @Override
    public Optional<IdentitySnapshot> load(long userId) {
        UserEntity user = userMapper.findById(userId);
        if (user == null || user.getStatus() == null || user.getStatus() == 2) {
            return Optional.empty();
        }
        Instant now = Instant.now();
        return Optional.of(new IdentitySnapshot(
                user.getId(),
                user.getTokenVersion() == null ? 0 : user.getTokenVersion(),
                user.getStatus() == 1,
                user.getMustChangePassword() != null && user.getMustChangePassword() == 1,
                now,
                now.plusSeconds(30)));
    }
}
