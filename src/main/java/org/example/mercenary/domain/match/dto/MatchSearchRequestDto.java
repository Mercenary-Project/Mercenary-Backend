package org.example.mercenary.domain.match.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class MatchSearchRequestDto {

    @Schema(description = "검색 기준 위도", example = "37.498095")
    @NotNull
    @Min(-90)
    @Max(90)
    private Double latitude;

    @Schema(description = "검색 기준 경도", example = "127.027610")
    @NotNull
    @Min(-180)
    @Max(180)
    private Double longitude;

    @Schema(description = "검색 반경(km)", example = "5")
    @NotNull
    @Min(1)
    @Max(50)
    private Double distance;
}
