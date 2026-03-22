package org.example.mercenary.global.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.example.mercenary.global.dto.AuthErrorResponseDto;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Component
@RequiredArgsConstructor
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    @Override
    public void commence(HttpServletRequest request,
                         HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        String errorCode = (String) request.getAttribute(JwtAuthenticationFilter.AUTH_EXCEPTION_ATTRIBUTE);

        if (errorCode == null) {
            errorCode = JwtAuthenticationFilter.TOKEN_MISSING;
        }

        String message = switch (errorCode) {
            case JwtAuthenticationFilter.TOKEN_EXPIRED -> "만료된 토큰입니다.";
            case JwtAuthenticationFilter.TOKEN_INVALID -> "유효하지 않은 토큰입니다.";
            default -> "인증 토큰이 없습니다.";
        };

        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(), new AuthErrorResponseDto(errorCode, message));
    }
}
