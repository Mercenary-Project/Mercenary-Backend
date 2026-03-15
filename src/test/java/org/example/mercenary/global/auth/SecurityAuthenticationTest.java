package org.example.mercenary.global.auth;

import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import org.example.mercenary.domain.application.controller.ApplicationController;
import org.example.mercenary.domain.application.dto.MyApplicationStatusResponseDto;
import org.example.mercenary.domain.application.service.ApplicationService;
import org.example.mercenary.domain.match.controller.MatchController;
import org.example.mercenary.domain.match.service.MatchService;
import org.example.mercenary.global.config.SecurityConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Date;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {MatchController.class, ApplicationController.class})
@Import({
        SecurityConfig.class,
        JwtAuthenticationFilter.class,
        RestAuthenticationEntryPoint.class,
        RestAccessDeniedHandler.class
})
class SecurityAuthenticationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private MatchService matchService;

    @MockBean
    private ApplicationService applicationService;

    @MockBean
    private JwtTokenProvider jwtTokenProvider;

    @MockBean
    private JpaMetamodelMappingContext jpaMetamodelMappingContext;

    @Test
    @DisplayName("토큰이 없으면 POST /api/matches 는 401 응답을 반환한다")
    void shouldReturnUnauthorizedWhenCreateMatchTokenMissing() throws Exception {
        mockMvc.perform(post("/api/matches")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validCreateMatchRequest()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401))
                .andExpect(jsonPath("$.message").value("인증이 필요합니다."));

        then(matchService).should(never()).createMatch(any(), any());
    }

    @Test
    @DisplayName("만료된 토큰이면 401 응답을 반환한다")
    void shouldReturnUnauthorizedWhenTokenExpired() throws Exception {
        given(jwtTokenProvider.getAuthentication("expired-token"))
                .willThrow(new ExpiredJwtException(
                        Jwts.header(),
                        Jwts.claims().setSubject("1").setExpiration(new Date(System.currentTimeMillis() - 1_000)),
                        "expired"
                ));

        mockMvc.perform(post("/api/matches/1/apply")
                        .header("Authorization", "Bearer expired-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401))
                .andExpect(jsonPath("$.message").value("만료된 토큰입니다."));
    }

    @Test
    @DisplayName("유효하지 않은 토큰이면 401 응답을 반환한다")
    void shouldReturnUnauthorizedWhenTokenInvalid() throws Exception {
        given(jwtTokenProvider.getAuthentication("invalid-token"))
                .willThrow(new MalformedJwtException("invalid"));

        mockMvc.perform(post("/api/matches/1/apply")
                        .header("Authorization", "Bearer invalid-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401))
                .andExpect(jsonPath("$.message").value("유효하지 않은 토큰입니다."));
    }

    @Test
    @DisplayName("정상 토큰이면 memberId 를 주입해 참가 신청을 처리한다")
    void shouldApplyMatchWhenTokenValid() throws Exception {
        given(jwtTokenProvider.getAuthentication("valid-token"))
                .willReturn(authenticatedUser(7L));

        mockMvc.perform(post("/api/matches/3/apply")
                        .header("Authorization", "Bearer valid-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        then(applicationService).should().applyMatch(3L, 7L);
    }

    @Test
    @DisplayName("정상 토큰이면 내 참가 신청 상태를 조회한다")
    void shouldGetMyApplicationStatusWhenTokenValid() throws Exception {
        given(jwtTokenProvider.getAuthentication("valid-token"))
                .willReturn(authenticatedUser(7L));
        given(applicationService.getMyApplicationStatus(3L, 7L))
                .willReturn(MyApplicationStatusResponseDto.builder()
                        .applied(true)
                        .status("READY")
                        .applicationId(10L)
                        .build());

        mockMvc.perform(get("/api/matches/3/application/me")
                        .header("Authorization", "Bearer valid-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.applied").value(true))
                .andExpect(jsonPath("$.data.status").value("READY"));
    }

    @Test
    @DisplayName("정상 토큰이면 내가 작성한 매치 목록을 조회한다")
    void shouldGetMyMatchesWhenTokenValid() throws Exception {
        given(jwtTokenProvider.getAuthentication("valid-token"))
                .willReturn(authenticatedUser(7L));
        given(matchService.getMyMatches(7L))
                .willReturn(java.util.List.of(
                        org.example.mercenary.domain.match.dto.MatchSearchResponseDto.builder()
                                .matchId(11L)
                                .title("my match")
                                .placeName("place")
                                .content("content")
                                .matchDate(java.time.LocalDateTime.of(2030, 1, 1, 10, 0))
                                .currentPlayerCount(3)
                                .maxPlayerCount(10)
                                .build()
                ));

        mockMvc.perform(get("/api/matches/my")
                        .header("Authorization", "Bearer valid-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].matchId").value(11L))
                .andExpect(jsonPath("$.data[0].title").value("my match"));

        then(matchService).should().getMyMatches(7L);
    }

    @Test
    @DisplayName("토큰이 없으면 내가 작성한 매치 목록 조회는 401을 반환한다")
    void shouldReturnUnauthorizedWhenGetMyMatchesTokenMissing() throws Exception {
        mockMvc.perform(get("/api/matches/my"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));

        then(matchService).should(never()).getMyMatches(any());
    }

    @Test
    @WithMockUser(roles = "GUEST")
    @DisplayName("권한이 부족하면 신청 목록 조회는 403 응답을 반환한다")
    void shouldReturnForbiddenWhenRoleInsufficientForApplications() throws Exception {
        mockMvc.perform(get("/api/matches/3/applications"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403))
                .andExpect(jsonPath("$.message").value("접근 권한이 없습니다."));
    }

    @Test
    @DisplayName("정상 토큰이면 신청 상태를 변경할 수 있다")
    void shouldUpdateApplicationStatusWhenTokenValid() throws Exception {
        given(jwtTokenProvider.getAuthentication("valid-token"))
                .willReturn(authenticatedUser(7L));

        mockMvc.perform(patch("/api/matches/3/applications/10")
                        .header("Authorization", "Bearer valid-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "status": "APPROVED"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        then(applicationService).should().updateApplicationStatus(3L, 10L, 7L, org.example.mercenary.domain.application.entity.ApplicationStatus.APPROVED);
    }

    @Test
    @WithMockUser(roles = "GUEST")
    @DisplayName("권한이 부족하면 POST /api/matches 는 403 응답을 반환한다")
    void shouldReturnForbiddenWhenRoleInsufficient() throws Exception {
        mockMvc.perform(post("/api/matches")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validCreateMatchRequest()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403))
                .andExpect(jsonPath("$.message").value("접근 권한이 없습니다."));
    }

    @Test
    @DisplayName("현재 인원이 1 미만이면 400 응답을 반환한다")
    void shouldReturnBadRequestWhenCurrentPlayerCountLessThanOne() throws Exception {
        given(jwtTokenProvider.getAuthentication("valid-token"))
                .willReturn(authenticatedUser(7L));

        mockMvc.perform(post("/api/matches")
                        .header("Authorization", "Bearer valid-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validCreateMatchRequest().replace("\"currentPlayerCount\": 1", "\"currentPlayerCount\": 0")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("현재 인원은 1명 이상이어야 합니다."));

        then(matchService).should(never()).createMatch(any(), any());
    }

    @Test
    @DisplayName("유효한 요청과 토큰이면 매치 생성 시 201 응답을 반환한다")
    void shouldCreateMatchWhenRequestValid() throws Exception {
        given(jwtTokenProvider.getAuthentication("valid-token"))
                .willReturn(authenticatedUser(7L));
        given(matchService.createMatch(any(), eq(7L))).willReturn(99L);

        mockMvc.perform(post("/api/matches")
                        .header("Authorization", "Bearer valid-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validCreateMatchRequest()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").value(99L));

        then(matchService).should().createMatch(any(), eq(7L));
    }

    private UsernamePasswordAuthenticationToken authenticatedUser(Long memberId) {
        return new UsernamePasswordAuthenticationToken(
                new AuthenticatedMember(memberId, "ROLE_USER"),
                "valid-token",
                AuthorityUtils.createAuthorityList("ROLE_USER")
        );
    }

    private String validCreateMatchRequest() {
        return """
                {
                  "title": "test",
                  "content": "content",
                  "placeName": "place",
                  "district": "district",
                  "fullAddress": "address",
                  "latitude": 37.5,
                  "longitude": 127.0,
                  "matchDate": "2030-01-01T10:00",
                  "maxPlayerCount": 10,
                  "currentPlayerCount": 1
                }
                """;
    }
}
