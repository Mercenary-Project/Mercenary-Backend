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
@NoArgsConstructor // 기본 생성자 필수
public class MatchCreateRequestDto {


    @NotBlank(message = "제목을 입력해주세요.")
    private String title;


    @NotBlank(message = "내용을 입력해주세요.")
    private String content;


    @NotBlank
    private String placeName;

    @NotBlank
    private String district;

    @NotBlank
    private String fullAddress;


    @NotNull
    private Double latitude;

    @NotNull
    private Double longitude;

    //경기 날짜 (String -> LocalDateTime으로 변경, 자동 변환 설정)
    @NotNull(message = "경기 날짜를 선택해주세요.")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm")
    private LocalDateTime matchDate;

    //모집 인원
    @NotNull
    @Min(2) @Max(22)
    private Integer maxPlayerCount;

    @NotNull
    private Integer currentPlayerCount;
}