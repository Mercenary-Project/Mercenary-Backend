package org.example.mercenary.domain.match.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class MatchCreateRequestDto {

    @Schema(description = "게시글 제목", example = "토요일 오전 풋살 같이 하실 분")
    @NotBlank(message = "제목을 입력해 주세요.")
    private String title;

    @Schema(description = "게시글 내용", example = "2명 모집합니다. 경험자 우대입니다.")
    @NotBlank(message = "내용을 입력해 주세요.")
    private String content;

    @Schema(description = "장소 이름", example = "탄천 풋살장")
    @NotBlank(message = "장소명을 입력해 주세요.")
    private String placeName;

    @Schema(description = "행정구역", example = "강남구")
    @NotBlank(message = "지역명을 입력해 주세요.")
    private String district;

    @Schema(description = "전체 주소", example = "서울특별시 강남구 탄천로 123")
    @NotBlank(message = "전체 주소를 입력해 주세요.")
    private String fullAddress;

    @Schema(description = "위도", example = "37.498095")
    @NotNull(message = "위도를 입력해 주세요.")
    private Double latitude;

    @Schema(description = "경도", example = "127.027610")
    @NotNull(message = "경도를 입력해 주세요.")
    private Double longitude;

    @Schema(description = "경기 일시", example = "2030-01-01T10:00")
    @NotNull(message = "경기 날짜를 선택해 주세요.")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm")
    private LocalDateTime matchDate;

    @Schema(description = "최대 인원", example = "10")
    @NotNull(message = "최대 인원을 입력해 주세요.")
    @Min(value = 2, message = "최대 인원은 2명 이상이어야 합니다.")
    @Max(value = 22, message = "최대 인원은 22명 이하여야 합니다.")
    private Integer maxPlayerCount;

    @Schema(description = "현재 인원", example = "6")
    @NotNull(message = "현재 인원을 입력해 주세요.")
    @Min(value = 1, message = "현재 인원은 1명 이상이어야 합니다.")
    private Integer currentPlayerCount;
}
