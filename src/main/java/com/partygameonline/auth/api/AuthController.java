package com.partygameonline.auth.api;

import com.partygameonline.auth.api.dto.AuthRequest;
import com.partygameonline.auth.api.dto.AuthResponse;
import com.partygameonline.auth.application.AuthService;
import com.partygameonline.session.domain.PlayerPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody AuthRequest body,
                                                 HttpServletRequest request, HttpServletResponse response) {
        PlayerPrincipal principal = authService.register(body.username(), body.password(), request, response);
        return ResponseEntity.status(HttpStatus.CREATED).body(AuthResponse.from(principal));
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody AuthRequest body,
                              HttpServletRequest request, HttpServletResponse response) {
        return AuthResponse.from(authService.login(body.username(), body.password(), request, response));
    }
}
