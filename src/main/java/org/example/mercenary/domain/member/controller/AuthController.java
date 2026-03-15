package org.example.mercenary.domain.member.controller;

import lombok.RequiredArgsConstructor;
import jakarta.validation.Valid;
import org.example.mercenary.domain.member.dto.AuthTokenResponse;
import org.example.mercenary.domain.member.dto.DevLoginRequest;
import org.example.mercenary.domain.member.service.AuthService;
import org.example.mercenary.global.dto.ApiResponseDto;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController //
@RequestMapping("/api/auth") //
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @Value("${auth.dev-login-enabled:false}")
    private boolean devLoginEnabled;

    @PostMapping("/kakao") //
    public ResponseEntity<ApiResponseDto<?>> kakaoLogin(@RequestBody Map<String, String> request) {
        String code = request.get("code");


        System.out.println("프론트에서 받은 인가 코드: " + code); // 로그 찍어보기
        // 서비스 호출
        AuthTokenResponse response = authService.login(code);

        // 결과 JSON 포장
        return ResponseEntity.ok(ApiResponseDto.success("카카오 로그인 성공", response));
    }

    @PostMapping("/dev-login")
    public ResponseEntity<ApiResponseDto<?>> devLogin(@Valid @RequestBody DevLoginRequest request) {
        if (!devLoginEnabled) {
            return ResponseEntity.status(403)
                    .body(ApiResponseDto.error(403, "개발용 로그인은 비활성화되어 있습니다."));
        }

        return ResponseEntity.ok(ApiResponseDto.success("개발용 로그인 성공", authService.devLogin(request)));
    }
}
