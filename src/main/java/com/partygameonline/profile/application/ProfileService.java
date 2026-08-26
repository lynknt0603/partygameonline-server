package com.partygameonline.profile.application;

import com.partygameonline.common.error.ApiException;
import com.partygameonline.room.application.RoomService;
import com.partygameonline.security.PlayerAuthentication;
import com.partygameonline.session.domain.PlayerPrincipal;
import com.partygameonline.session.domain.SessionKind;
import com.partygameonline.user.infrastructure.UserEntity;
import com.partygameonline.user.infrastructure.UserJpaRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProfileService {

    private final UserJpaRepository userRepository;
    private final RoomService roomService;
    private final SecurityContextRepository securityContextRepository = new HttpSessionSecurityContextRepository();

    public ProfileService(UserJpaRepository userRepository, RoomService roomService) {
        this.userRepository = userRepository;
        this.roomService = roomService;
    }

    @Transactional
    public PlayerPrincipal updateDisplayName(
            PlayerPrincipal current,
            String requestedDisplayName,
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        String displayName = requestedDisplayName == null ? "" : requestedDisplayName.trim();
        if (displayName.isEmpty()) {
            throw new ApiException("INVALID_DISPLAY_NAME", HttpStatus.BAD_REQUEST, "Display name is required");
        }

        if (current.kind() == SessionKind.MEMBER) {
            UserEntity user = userRepository.findByUserKey(current.playerId())
                    .orElseThrow(() -> new ApiException(
                            "PROFILE_NOT_FOUND", HttpStatus.NOT_FOUND, "Player profile was not found"
                    ));
            user.rename(displayName);
        }

        PlayerPrincipal updated = current.withDisplayName(displayName);
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new PlayerAuthentication(updated));
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, request, response);
        roomService.syncPlayerDisplayName(updated.playerId(), updated.displayName());
        return updated;
    }
}
