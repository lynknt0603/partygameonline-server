package com.partygameonline.security.api;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/csrf")
public class CsrfController {

    static final String COOKIE_NAME = "XSRF-TOKEN";

    @GetMapping
    public CsrfTokenResponse token(CsrfToken csrfToken, HttpServletResponse response) {
        String token = csrfToken.getToken();
        ResponseCookie cookie = ResponseCookie.from(COOKIE_NAME, token)
                .httpOnly(false)
                .path("/")
                .sameSite("Lax")
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
        return new CsrfTokenResponse(csrfToken.getHeaderName(), csrfToken.getParameterName(), token);
    }

    public record CsrfTokenResponse(String headerName, String parameterName, String token) {
    }
}
