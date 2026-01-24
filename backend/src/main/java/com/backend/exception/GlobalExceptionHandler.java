package com.backend.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {
    
    @ExceptionHandler(RoutineNotFoundException.class)
    public ResponseEntity<Map<String, String>> handleRoutineNotFoundException(RoutineNotFoundException e) {
        log.error("루틴을 찾을 수 없음: {}", e.getMessage());
        Map<String, String> error = new HashMap<>();
        error.put("error", "루틴을 찾을 수 없습니다.");
        error.put("message", e.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
    }
    
    @ExceptionHandler(ExerciseNotFoundException.class)
    public ResponseEntity<Map<String, String>> handleExerciseNotFoundException(ExerciseNotFoundException e) {
        log.error("운동을 찾을 수 없음: {}", e.getMessage());
        Map<String, String> error = new HashMap<>();
        error.put("error", "운동을 찾을 수 없습니다.");
        error.put("message", e.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
    }
    
    @ExceptionHandler(MemberNotFoundException.class)
    public ResponseEntity<Map<String, String>> handleMemberNotFoundException(MemberNotFoundException e) {
        log.error("회원을 찾을 수 없음: {}", e.getMessage());
        Map<String, String> error = new HashMap<>();
        error.put("error", "회원을 찾을 수 없습니다.");
        error.put("message", e.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
    }
    
    @ExceptionHandler(AIServerException.class)
    public ResponseEntity<Map<String, String>> handleAIServerException(AIServerException e) {
        log.error("AI 서버 오류: {}", e.getMessage(), e);
        Map<String, String> error = new HashMap<>();
        error.put("error", "AI 서버 통신 실패");
        error.put("message", e.getMessage());
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(error);
    }
    
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleIllegalArgumentException(IllegalArgumentException e) {
        log.error("잘못된 요청: {}", e.getMessage());
        Map<String, String> error = new HashMap<>();
        error.put("error", "잘못된 요청입니다.");
        error.put("message", e.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }
    
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleException(Exception e) {
        log.error("예상치 못한 오류 발생: {}", e.getMessage(), e);
        Map<String, String> error = new HashMap<>();
        error.put("error", "서버 오류가 발생했습니다.");
        error.put("message", e.getMessage());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
    }
}
