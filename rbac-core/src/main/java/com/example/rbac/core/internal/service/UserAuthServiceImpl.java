package com.example.rbac.core.internal.service;

import com.example.rbac.api.auth.AuthAccount;
import com.example.rbac.api.service.UserAuthService;
import com.example.rbac.core.internal.domain.UserEntity;
import com.example.rbac.core.internal.mapper.UserMapper;
import com.example.rbac.core.internal.support.TimeSupport;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
public class UserAuthServiceImpl implements UserAuthService {

    private static final int MAX_FAILURES = 5;
    private static final int LOCK_MINUTES = 15;

    private final UserMapper userMapper;

    public UserAuthServiceImpl(UserMapper userMapper) {
        this.userMapper = userMapper;
    }

    @Override
    @Transactional
    public Optional<AuthAccount> findForLogin(String username) {
        UserEntity user = userMapper.findByUsername(username);
        if (user == null) {
            return Optional.empty();
        }
        return Optional.of(new AuthAccount(
                user.getId(),
                user.getUsername(),
                user.getNickname(),
                user.getPasswordHash(),
                user.getStatus() == null ? 0 : user.getStatus(),
                user.getMustChangePassword() != null && user.getMustChangePassword() == 1,
                user.getTokenVersion() == null ? 0 : user.getTokenVersion(),
                user.getFailCount() == null ? 0 : user.getFailCount(),
                TimeSupport.toInstant(user.getLockedUntil())));
    }

    @Override
    @Transactional
    public int incrementTokenVersion(long userId) {
        userMapper.incrementTokenVersion(userId, false, TimeSupport.nowUtc());
        UserEntity user = userMapper.findById(userId);
        return user == null || user.getTokenVersion() == null ? 0 : user.getTokenVersion();
    }

    @Override
    @Transactional
    public void recordLoginFailure(long userId) {
        LocalDateTime now = TimeSupport.nowUtc();
        UserEntity user = userMapper.findById(userId);
        if (user == null) {
            return;
        }
        userMapper.recordLoginFailure(userId, MAX_FAILURES, now.plusMinutes(LOCK_MINUTES), now);
    }

    @Override
    @Transactional
    public void recordLoginSuccess(long userId) {
        LocalDateTime now = TimeSupport.nowUtc();
        userMapper.clearLoginFailure(userId, now, now);
    }

    @Override
    @Transactional
    public void resetLoginFailures(long userId) {
        userMapper.resetLoginFailures(userId, TimeSupport.nowUtc());
    }

    @Override
    @Transactional
    public int changePassword(long userId, String newPasswordHash) {
        LocalDateTime now = TimeSupport.nowUtc();
        userMapper.updatePassword(userId, newPasswordHash, false, userId, now);
        userMapper.incrementTokenVersion(userId, false, now);
        UserEntity user = userMapper.findById(userId);
        return user == null || user.getTokenVersion() == null ? 0 : user.getTokenVersion();
    }
}
