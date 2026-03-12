package org.example.mercenary.global.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.example.mercenary.global.dto.ApiResponseDto;
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
        String message = "인증이 필요합니다.";

        if (JwtAuthenticationFilter.TOKEN_EXPIRED.equals(errorCode)) {
            message = "만료된 토큰입니다.";
        } else if (JwtAuthenticationFilter.TOKEN_INVALID.equals(errorCode)) {
            message = "유효하지 않은 토큰입니다.";
        }

        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(), ApiResponseDto.error(401, message));
    }
}
