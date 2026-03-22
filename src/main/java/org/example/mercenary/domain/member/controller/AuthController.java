package org.example.mercenary.domain.member.controller;

import jakarta.validation.Valid;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.example.mercenary.domain.member.dto.AuthTokenResponse;
import org.example.mercenary.domain.member.dto.DevLoginRequest;
import org.example.mercenary.domain.member.service.AuthService;
import org.example.mercenary.global.dto.ApiResponseDto;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @Value("${auth.dev-login-enabled:false}")
    private boolean devLoginEnabled;

    @PostMapping("/kakao")
    public ResponseEntity<ApiResponseDto<?>> kakaoLogin(@RequestBody Map<String, String> request) {
        String code = request.get("code");
        AuthTokenResponse response = authService.login(code);
        return ResponseEntity.ok(ApiResponseDto.success("카카오 로그인 성공", response));
    }

    @PostMapping("/dev-login")
    public ResponseEntity<ApiResponseDto<?>> devLogin(@Valid @RequestBody DevLoginRequest request) {
        if (!devLoginEnabled) {
            return ResponseEntity.status(403)
                    .body(ApiResponseDto.error(403, "개발용 로그인이 비활성화되어 있습니다."));
        }

        return ResponseEntity.ok(ApiResponseDto.success("개발용 로그인 성공", authService.devLogin(request)));
    }
}
