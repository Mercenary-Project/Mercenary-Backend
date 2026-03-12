package org.example.mercenary.global.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class ApiResponseDto<T> {

    // HTTP Status 대신 API 상태를 나타내는 코드 (예: 200)
    private final int code;

    // 응답 메시지 (예: "신청 성공")
    private final String message;

    // 실제 데이터
    private final T data;

    // 성공 시 사용하는 정적 팩토리 메서드
    public static <T> ApiResponseDto<T> success(String message, T data) {
        return new ApiResponseDto<>(200, message, data);
    }

    // 데이터 없이 성공 메시지만 보낼 때
    public static ApiResponseDto<?> success(String message) {
        return new ApiResponseDto<>(200, message, null);
    }

    //에러 발생 시
    public static ApiResponseDto<?> error(int code, String message) {
        return new ApiResponseDto<>(code, message, null);
    }
}