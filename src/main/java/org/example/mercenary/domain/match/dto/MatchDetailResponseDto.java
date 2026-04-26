package org.example.mercenary.domain.match.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.example.mercenary.domain.match.entity.MatchEntity;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MatchDetailResponseDto {
    private Long matchId;
    private String title;
    private String content;
    private String placeName;
    private String fullAddress;
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime matchDate;
    private String status;
    private String writerName;
    private List<PositionSlotResponseDto> slots;
    private boolean isFullyBooked;

    public static MatchDetailResponseDto from(MatchEntity match) {
        return MatchDetailResponseDto.builder()
                .matchId(match.getId())
                .title(match.getTitle())
                .content(match.getContent())
                .placeName(match.getPlaceName())
                .fullAddress(match.getFullAddress())
                .matchDate(match.getMatchDate())
                .status(match.getStatus().name())
                .writerName(match.getMember() != null ? match.getMember().getNickname() : "알 수 없음")
                .slots(match.getSlots().stream()
                        .map(PositionSlotResponseDto::from)
                        .collect(Collectors.toList()))
                .isFullyBooked(match.isFullyBooked())
                .build();
    }
}
