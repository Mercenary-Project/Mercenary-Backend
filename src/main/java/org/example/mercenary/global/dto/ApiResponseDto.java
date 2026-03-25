package org.example.mercenary.global.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
@Schema(description = "공통 API 응답 형식")
public class ApiResponseDto<T> {

    @Schema(description = "응답 코드", example = "200")
    private final int code;

    @Schema(description = "응답 메시지", example = "요청 성공")
    private final String message;

    @Schema(description = "실제 응답 데이터")
    private final T data;

    public static <T> ApiResponseDto<T> success(String message, T data) {
        return new ApiResponseDto<>(200, message, data);
    }

    public static ApiResponseDto<?> success(String message) {
        return new ApiResponseDto<>(200, message, null);
    }

    public static ApiResponseDto<?> error(int code, String message) {
        return new ApiResponseDto<>(code, message, null);
    }
}
