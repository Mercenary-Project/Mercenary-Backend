package org.example.mercenary.domain.member.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.mercenary.domain.member.dto.AuthTokenResponse;
import org.example.mercenary.domain.member.dto.DevLoginRequest;
import org.example.mercenary.domain.member.dto.KakaoLoginRequest;
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
@Tag(name = "Auth", description = "인증 API")
public class AuthController {

    private final AuthService authService;

    @Value("${auth.dev-login-enabled:false}")
    private boolean devLoginEnabled;

    @Operation(summary = "카카오 로그인", description = "카카오 인가 코드를 받아 자체 JWT를 발급합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "로그인 성공"),
            @ApiResponse(responseCode = "400", description = "인가 코드 누락 또는 잘못된 요청"),
            @ApiResponse(responseCode = "500", description = "카카오 로그인 처리 실패")
    })
    @PostMapping("/kakao")
    public ResponseEntity<ApiResponseDto<?>> kakaoLogin(@Valid @RequestBody KakaoLoginRequest request) {
        AuthTokenResponse response = authService.login(request.code());
        return ResponseEntity.ok(ApiResponseDto.success("카카오 로그인 성공", response));
    }

    @Operation(summary = "개발용 로그인", description = "dev 환경에서만 사용 가능한 테스트 로그인입니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "로그인 성공"),
            @ApiResponse(responseCode = "403", description = "개발용 로그인 비활성화")
    })
    @PostMapping("/dev-login")
    public ResponseEntity<ApiResponseDto<?>> devLogin(@Valid @RequestBody DevLoginRequest request) {
        if (!devLoginEnabled) {
            return ResponseEntity.status(403)
                    .body(ApiResponseDto.error(403, "개발용 로그인이 비활성화되어 있습니다."));
        }

        return ResponseEntity.ok(ApiResponseDto.success("개발용 로그인 성공", authService.devLogin(request)));
    }
}
