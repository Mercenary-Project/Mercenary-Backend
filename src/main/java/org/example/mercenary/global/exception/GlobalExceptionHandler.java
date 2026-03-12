package org.example.mercenary.global.exception;

import org.example.mercenary.global.dto.ApiResponseDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice // 모든 Controller에서 발생하는 예외를 감지합니다.
public class GlobalExceptionHandler {

    /**
     * 1. 비즈니스 로직 예외 (예: 존재하지 않는 경기, 이미 신청한 경기 등)
     * - IllegalArgumentException, IllegalStateException 등을 400 Bad Request로 처리
     */
    @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
    public ResponseEntity<ApiResponseDto<?>> handleBusinessException(RuntimeException e) {
        log.warn("Business Exception: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponseDto.error(400, e.getMessage()));
    }

    /**
     * 2. 유효성 검사 실패 예외 (@Valid 실패 시)
     * - DTO의 @NotNull, @Min 등이 실패했을 때 발생
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponseDto<?>> handleValidationException(MethodArgumentNotValidException e) {
        BindingResult bindingResult = e.getBindingResult();
        String firstErrorMessage = bindingResult.getAllErrors().get(0).getDefaultMessage();

        log.warn("Validation Failed: {}", firstErrorMessage);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponseDto.error(400, firstErrorMessage));
    }

    /**
     * 3. 나머지 모든 알 수 없는 예외 (서버 에러)
     * - 예상을 빗나간 에러는 500으로 처리하되, 상세 내용은 로그로 남김
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponseDto<?>> handleException(Exception e) {
        log.error("Unhandled Exception: ", e); // 스택 트레이스 출력
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponseDto.error(500, "서버 내부 오류가 발생했습니다. 관리자에게 문의하세요."));
    }
}