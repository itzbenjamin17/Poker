package com.pokergame.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.pokergame.dto.response.ErrorResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

@ControllerAdvice
public class ControllerExceptionHandler {
    private static final Logger logger = LoggerFactory.getLogger(ControllerExceptionHandler.class);

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgumentException(IllegalArgumentException ex) {
        logger.error("Invalid request", ex);
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ErrorResponse> IllegalStateException(IllegalStateException ex){
        logger.error("Invalid state", ex);
    }

    @ExceptionHandler(SecurityException.class)
    public ResponseEntity<ErrorResponse> SecurityException(SecurityException ex){
        logger.error("Security violation", ex);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleException(Exception ex) {
        logger.error("Unexpected error", ex);
    }
}
