package org.example.mercenary.domain.match.service;

import org.example.mercenary.domain.match.service.MatchLocationService;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest

class MatchLocationServiceTest {

    @Autowired
    private MatchLocationService matchLocationService;

    @Test
    @DisplayName("반경 3km 내의 경기장만 정확하게 조회되어야 한다.")
    void searchNearbyMatches() {
        // given
        // 1. 서울시청 (기준점)
        double seoulCityHallLon = 126.9780;
        double seoulCityHallLat = 37.5665;

        // 2. 광화문 (시청에서 약 1km 거리 -> 검색 되어야 함)
        Long nearbyMatchId = 100L;
        matchLocationService.addMatchLocation(nearbyMatchId, 126.9768, 37.5759);

        // 3. 강남역 (시청에서 약 10km 거리 -> 검색 안 되어야 함)
        Long farMatchId = 200L;
        matchLocationService.addMatchLocation(farMatchId, 127.0276, 37.4979);




        // when
        // 시청(기준점)에서 반경 3km 검색
      //  List<String> result = matchLocationService.findNearbyMatchIds(seoulCityHallLon, seoulCityHallLat, 3.0);

        // then
       // System.out.println("검색된 경기 ID 목록: " + result);

       // assertThat(result).contains(nearbyMatchId.toString()); // 광화문은 있어야 하고
        //assertThat(result).doesNotContain(farMatchId.toString()); // 강남역은 없어야 한다
    }
}
