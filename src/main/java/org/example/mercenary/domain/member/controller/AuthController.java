package org.example.mercenary.domain.member.controller;

import lombok.RequiredArgsConstructor;
import org.example.mercenary.domain.member.service.AuthService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController //
@RequestMapping("/api/auth") //
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/kakao") //
    public ResponseEntity<Map<String, String>> kakaoLogin(@RequestBody Map<String, String> request) {
        String code = request.get("code");


        System.out.println("프론트에서 받은 인가 코드: " + code); // 로그 찍어보기
        // 서비스 호출
        String accessToken = authService.login(code);

        // 결과 JSON 포장
        Map<String, String> response = new HashMap<>();
        response.put("accessToken", accessToken);

        return ResponseEntity.ok(response);
    }
}