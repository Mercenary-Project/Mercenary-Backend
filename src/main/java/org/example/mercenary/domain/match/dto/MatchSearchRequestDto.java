package org.example.mercenary.domain.match.dto;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor // 기본 생성자 (필수!)
@AllArgsConstructor // 모든 필드 생성자
public class MatchSearchRequestDto {
    @NotNull @Min(-90) @Max(90)
    private Double latitude;
    @NotNull @Min(-180) @Max(180)
    private Double longitude;

    // 검색 반경 (Km 단위)
    @NotNull @Min(1) @Max(50) // 최소 1km, 최대 50km까지 검색 허용
    private Double distance;
}
