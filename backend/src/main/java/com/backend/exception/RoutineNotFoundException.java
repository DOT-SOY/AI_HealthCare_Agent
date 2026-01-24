package com.backend.exception;

public class RoutineNotFoundException extends RuntimeException {
    public RoutineNotFoundException(String message) {
        super(message);
    }
}

