package com.backend.exception;

public class AIServerException extends RuntimeException {
    public AIServerException(String message) {
        super(message);
    }
    
    public AIServerException(String message, Throwable cause) {
        super(message, cause);
    }
}

