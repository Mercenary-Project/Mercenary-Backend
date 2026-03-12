package org.example.mercenary.domain.member.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.mercenary.domain.member.dto.KakaoTokenResponse;
import org.example.mercenary.domain.member.dto.KakaoUserInfoResponse;
import org.example.mercenary.domain.member.entity.MemberEntity;
import org.example.mercenary.domain.member.entity.Role;
import org.example.mercenary.domain.member.repository.MemberRepository;
import org.example.mercenary.global.auth.JwtTokenProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    @Value("${kakao.client-id}")
    private String clientId;

    private final MemberRepository memberRepository;
    private final JwtTokenProvider jwtTokenProvider;

    public String login(String code) {
        // 1. 카카오로부터 Access Token 받기
        String accessToken = getKakaoAccessToken(code);

        // 2. Access Token으로 카카오 유저 정보 가져오기
        KakaoUserInfoResponse userInfo = getKakaoUserInfo(accessToken);

        // 3. 우리 DB에 회원 저장 또는 업데이트 (방어 코드 추가)
        MemberEntity member = memberRepository.findByKakaoId(userInfo.getId())
                .orElseGet(() -> {
                    // 계정 정보가 없을 경우를 대비한 안전한 추출
                    String email = null;
                    String nickname = "용병_" + userInfo.getId(); // 기본 닉네임 설정

                    if (userInfo.getKakaoAccount() != null) {
                        email = userInfo.getKakaoAccount().getEmail();
                        if (userInfo.getKakaoAccount().getProfile() != null) {
                            nickname = userInfo.getKakaoAccount().getProfile().getNickname();
                        }
                    }

                    log.info("신규 회원 등록: kakaoId={}, nickname={}", userInfo.getId(), nickname);

                    return memberRepository.save(MemberEntity.builder()
                            .kakaoId(userInfo.getId())
                            .nickname(nickname)
                            .email(email)
                            .role(Role.USER) // Role 엔티티가 Role.USER 형태인지 확인하세요
                            .build());
                });

        // 4. 우리 서비스 전용 JWT 토큰 발급
        return jwtTokenProvider.createToken(member.getId(), member.getRole().name());
    }

    private String getKakaoAccessToken(String code) {
        WebClient webClient = WebClient.create("https://kauth.kakao.com");
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("grant_type", "authorization_code");
        params.add("client_id", clientId);
        params.add("redirect_uri", "http://localhost:5173/login/callback");
        params.add("code", code);
        // params.add("client_secret", "사용중이라면_여기에_키입력");

        try {
            KakaoTokenResponse response = webClient.post()
                    .uri("/oauth/token")
                    .header("Content-type", "application/x-www-form-urlencoded;charset=utf-8")
                    .bodyValue(params)
                    .retrieve()
                    .bodyToMono(KakaoTokenResponse.class)
                    .block();

            return response.getAccessToken();

        } catch (WebClientResponseException e) {
            // 🔥 여기가 핵심입니다! 카카오가 보낸 진짜 에러 내용을 로그에 찍습니다.
            log.error(">>>> 카카오 에러 응답(Body): {}", e.getResponseBodyAsString());
            throw new RuntimeException("카카오 토큰 요청 실패: " + e.getMessage());
        }
    }

    private KakaoUserInfoResponse getKakaoUserInfo(String accessToken) {
        WebClient webClient = WebClient.create("https://kapi.kakao.com");
        return webClient.get()
                .uri("/v2/user/me")
                .header("Authorization", "Bearer " + accessToken)
                .retrieve()
                .bodyToMono(KakaoUserInfoResponse.class)
                .block();
    }
}