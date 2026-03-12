package org.example.mercenary.domain.match.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.example.mercenary.domain.match.entity.MatchEntity;

import java.time.format.DateTimeFormatter;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MatchDetailResponseDto {
    private Long matchId;
    private String title;
    private String content;
    private String placeName;
    private String fullAddress; // 주소 추가
    private String matchDate;   // 보기 좋게 포맷팅된 날짜
    private int maxPlayerCount;     // 총 인원 (Entity와 이름 맞춤)
    private int currentPlayerCount; // 현재 인원 (Entity와 이름 맞춤)
    private String status;      // 모집중/마감 상태
    private String writerName;  // 작성자 이름

    // Entity -> DTO 변환 메서드
    public static MatchDetailResponseDto from(MatchEntity match) {
        return MatchDetailResponseDto.builder()
                .matchId(match.getId())
                .title(match.getTitle())
                .content(match.getContent())
                .placeName(match.getPlaceName())
                .fullAddress(match.getFullAddress()) // Entity에 있는 필드 활용
                .matchDate(match.getMatchDate().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))) // 날짜 예쁘게 변환
                .maxPlayerCount(match.getMaxPlayerCount()) // ✅ 수정됨: getTotalHeadCount -> getMaxPlayerCount
                .currentPlayerCount(match.getCurrentPlayerCount()) // ✅ 수정됨: Entity 값 그대로 사용
                .status(match.getStatus().name()) // Enum -> String 변환 (RECRUITING 등)
                .writerName(match.getMember() != null ? match.getMember().getNickname() : "알 수 없음")
                .build();
    }
}