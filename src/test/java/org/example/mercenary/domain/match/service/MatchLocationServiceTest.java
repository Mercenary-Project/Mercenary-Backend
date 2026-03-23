package org.example.mercenary.domain.match.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.geo.Circle;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.GeoResult;
import org.springframework.data.geo.GeoResults;
import org.springframework.data.geo.Metrics;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.GeoOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

@ExtendWith(MockitoExtension.class)
class MatchLocationServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private GeoOperations<String, String> geoOperations;

    @InjectMocks
    private MatchLocationService matchLocationService;

    @BeforeEach
    void setUp() {
        given(redisTemplate.opsForGeo()).willReturn(geoOperations);
    }

    @Test
    @DisplayName("반경 3km 내의 경기장만 정확하게 조회되어야 한다.")
    void searchNearbyMatches() {
        GeoResult<RedisGeoCommands.GeoLocation<String>> nearby = new GeoResult<>(
                new RedisGeoCommands.GeoLocation<>("100", new Point(126.9768, 37.5759)),
                new Distance(1.0, Metrics.KILOMETERS)
        );
        GeoResult<RedisGeoCommands.GeoLocation<String>> farAway = new GeoResult<>(
                new RedisGeoCommands.GeoLocation<>("200", new Point(127.0276, 37.4979)),
                new Distance(10.0, Metrics.KILOMETERS)
        );
        GeoResults<RedisGeoCommands.GeoLocation<String>> geoResults = new GeoResults<>(List.of(nearby, farAway));

        given(geoOperations.radius(eq("matches:geo"), any(Circle.class), any(RedisGeoCommands.GeoRadiusCommandArgs.class)))
                .willReturn(geoResults);

        var result = matchLocationService.findNearbyMatchIds(126.9780, 37.5665, 3.0);

        assertThat(result).containsEntry(100L, 1.0);
        assertThat(result).containsEntry(200L, 10.0);
    }

    @Test
    @DisplayName("경기 위치 등록 시 Redis GEO 저장소에 matchId가 추가되어야 한다.")
    void shouldAddMatchLocation() {
        matchLocationService.addMatchLocation(100L, 126.9768, 37.5759);

        then(geoOperations).should().add(eq("matches:geo"), any(Point.class), eq("100"));
    }

    @Test
    @DisplayName("경기 위치 삭제 시 Redis GEO 저장소에서 matchId가 제거되어야 한다.")
    void shouldDeleteMatchLocation() {
        matchLocationService.deleteMatchLocation(100L);

        then(geoOperations).should().remove("matches:geo", "100");
    }
}
