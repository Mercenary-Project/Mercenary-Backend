package org.example.mercenary.domain.member.controller;

import org.example.mercenary.domain.member.dto.AuthTokenResponse;
import org.example.mercenary.domain.member.dto.DevLoginRequest;
import org.example.mercenary.domain.member.service.AuthService;
import org.example.mercenary.global.auth.JwtTokenProvider;
import org.example.mercenary.global.config.TimeConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.http.MediaType;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
@AutoConfigureMockMvc(addFilters = false)
@TestPropertySource(properties = "auth.dev-login-enabled=true")
@Import(TimeConfig.class)
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AuthService authService;

    @MockBean
    private JwtTokenProvider jwtTokenProvider;

    @MockBean
    private JpaMetamodelMappingContext jpaMetamodelMappingContext;

    @Test
    @DisplayName("카카오 로그인 응답은 공통 API 래퍼를 따른다")
    void shouldWrapKakaoLoginResponse() throws Exception {
        given(authService.login("auth-code"))
                .willReturn(new AuthTokenResponse("jwt-token", 1L, "mercenary"));

        mockMvc.perform(post("/api/auth/kakao")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "code": "auth-code"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.accessToken").value("jwt-token"))
                .andExpect(jsonPath("$.data.memberId").value(1L))
                .andExpect(jsonPath("$.data.nickname").value("mercenary"));
    }

    @Test
    @DisplayName("개발용 로그인 응답은 공통 API 래퍼를 따른다")
    void shouldWrapDevLoginResponse() throws Exception {
        DevLoginRequest request = new DevLoginRequest(1004L, "mercenary");
        given(authService.devLogin(request))
                .willReturn(new AuthTokenResponse("dev-token", 2L, "mercenary"));

        mockMvc.perform(post("/api/auth/dev-login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "kakaoId": 1004,
                                  "nickname": "mercenary"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.accessToken").value("dev-token"))
                .andExpect(jsonPath("$.data.memberId").value(2L))
                .andExpect(jsonPath("$.data.nickname").value("mercenary"));
    }
}
