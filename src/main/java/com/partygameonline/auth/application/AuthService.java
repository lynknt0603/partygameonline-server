package com.partygameonline.auth.application;

import com.partygameonline.common.error.ApiException;
import com.partygameonline.common.avatar.AvatarCatalog;
import com.partygameonline.session.application.SessionService;
import com.partygameonline.session.domain.PlayerPrincipal;
import com.partygameonline.user.infrastructure.UserEntity;
import com.partygameonline.user.infrastructure.UserJpaRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Locale;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UserJpaRepository users;
    private final AesPasswordCipher passwordCipher;
    private final SessionService sessions;

    public AuthService(UserJpaRepository users, AesPasswordCipher passwordCipher, SessionService sessions) {
        this.users = users;
        this.passwordCipher = passwordCipher;
        this.sessions = sessions;
    }

    @Transactional
    public PlayerPrincipal register(String username, String password, String displayName,
                                    HttpServletRequest request, HttpServletResponse response) {
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
            return sessions.createMemberSession(
                    user.getUserKey(),
                    user.getDisplayName(),
                    user.getCreatedAt(),
                    AvatarCatalog.urlForKey(user.getAvatarKey()),
                    request,
                    response
            );
        } catch (DataIntegrityViolationException exception) {
            throw new ApiException("USERNAME_ALREADY_EXISTS", HttpStatus.CONFLICT, "Username is already in use");
        }
    }

    @Transactional(readOnly = true)
    public PlayerPrincipal login(String username, String password,
                                 HttpServletRequest request, HttpServletResponse response) {
        UserEntity user = users.findByUsername(normalize(username))
                .filter(candidate -> passwordCipher.matches(password, candidate.getPasswordAes()))
                .orElseThrow(() -> new ApiException(
                        "INVALID_CREDENTIALS", HttpStatus.UNAUTHORIZED, "Username or password is incorrect"
                ));
        return sessions.createMemberSession(
                user.getUserKey(),
                user.getDisplayName(),
                user.getCreatedAt(),
                AvatarCatalog.urlForKey(user.getAvatarKey()),
                request,
                response
        );
    }

    private String normalize(String username) {
        return username.trim().toLowerCase(Locale.ROOT);
    }
}
