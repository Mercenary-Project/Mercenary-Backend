package org.example.mercenary.domain.match.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
public class MatchUpdateRequestDto {

    @NotBlank(message = "제목을 입력해 주세요.")
    private String title;

    @NotBlank(message = "내용을 입력해 주세요.")
    private String content;

    @NotBlank(message = "장소명을 입력해 주세요.")
    private String placeName;

    @NotBlank(message = "지역명을 입력해 주세요.")
    private String district;

    @NotBlank(message = "전체 주소를 입력해 주세요.")
    private String fullAddress;

    @NotNull(message = "위도를 입력해 주세요.")
    private Double latitude;

    @NotNull(message = "경도를 입력해 주세요.")
    private Double longitude;

    @NotNull(message = "경기 날짜를 선택해 주세요.")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm")
    private LocalDateTime matchDate;

    @NotNull(message = "최대 인원을 입력해 주세요.")
    @Min(value = 2, message = "최대 인원은 2명 이상이어야 합니다.")
    @Max(value = 22, message = "최대 인원은 22명 이하여야 합니다.")
    private Integer maxPlayerCount;

    @NotNull(message = "현재 인원을 입력해 주세요.")
    @Min(value = 1, message = "현재 인원은 1명 이상이어야 합니다.")
    private Integer currentPlayerCount;
}
