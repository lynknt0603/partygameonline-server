package com.partygameonline.auth.application;

import com.partygameonline.common.error.ApiException;
import com.partygameonline.common.avatar.AvatarCatalog;
import com.partygameonline.session.application.SessionService;
import com.partygameonline.session.domain.PlayerPrincipal;
import com.partygameonline.user.infrastructure.UserEntity;
import com.partygameonline.user.infrastructure.UserJpaRepository;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Locale;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UserJpaRepository users;
    private final AesPasswordCipher passwordCipher;
    private final SessionService sessions;
    private final AuthRateLimiter authRateLimiter;

    @Autowired
    public AuthService(
            UserJpaRepository users,
            AesPasswordCipher passwordCipher,
            SessionService sessions,
            AuthRateLimiter authRateLimiter
    ) {
        this.users = users;
        this.passwordCipher = passwordCipher;
        this.sessions = sessions;
        this.authRateLimiter = authRateLimiter;
    }

    public AuthService(UserJpaRepository users, AesPasswordCipher passwordCipher, SessionService sessions) {
        this(users, passwordCipher, sessions, new AuthRateLimiter());
    }

    @Transactional
    public PlayerPrincipal register(String username, String password, String displayName) {
        String normalized = normalize(username);
        String normalizedDisplayName = displayName.trim();
        if (users.existsByUsername(normalized)) {
            throw new ApiException("USERNAME_ALREADY_EXISTS", HttpStatus.CONFLICT, "Username is already in use");
        }
        try {
            UserEntity user = users.saveAndFlush(UserEntity.newMember(
                    normalized,
                    passwordCipher.encrypt(password),
                    normalizedDisplayName
            ));
            return sessions.createMember(
                    user.getUserKey(),
                    user.getDisplayName(),
                    user.getCreatedAt(),
                    AvatarCatalog.urlForKey(user.getAvatarKey())
            );
        } catch (DataIntegrityViolationException exception) {
            throw new ApiException("USERNAME_ALREADY_EXISTS", HttpStatus.CONFLICT, "Username is already in use");
        }
    }

    @Transactional
    public PlayerPrincipal login(String username, String password, HttpServletRequest request) {
        if (!authRateLimiter.tryAcquire(request == null ? null : request.getRemoteAddr())) {
            throw new ApiException(
                    "AUTH_RATE_LIMITED", HttpStatus.TOO_MANY_REQUESTS, "Too many login attempts; please try again later"
            );
        }
        UserEntity user = users.findByUsername(normalize(username))
                .orElseThrow(() -> new ApiException(
                        "INVALID_CREDENTIALS", HttpStatus.UNAUTHORIZED, "Username or password is incorrect"
                ));
        if (!passwordCipher.matches(password, user.getPasswordAes())) {
            throw new ApiException(
                    "INVALID_CREDENTIALS", HttpStatus.UNAUTHORIZED, "Username or password is incorrect"
            );
        }
        if (passwordCipher.needsUpgrade(user.getPasswordAes())) {
            user.upgradePassword(passwordCipher.encrypt(password));
            users.save(user);
        }
        return sessions.createMember(
                user.getUserKey(),
                user.getDisplayName(),
                user.getCreatedAt(),
                AvatarCatalog.urlForKey(user.getAvatarKey())
        );
    }

    private String normalize(String username) {
        return username.trim().toLowerCase(Locale.ROOT);
    }
}
