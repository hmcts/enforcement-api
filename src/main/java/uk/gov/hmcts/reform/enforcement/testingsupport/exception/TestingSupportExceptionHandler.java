package uk.gov.hmcts.reform.enforcement.testingsupport.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.WebRequest;

@Slf4j
@ControllerAdvice
@ConditionalOnProperty(name = "testing-support.enabled", havingValue = "true")
public class TestingSupportExceptionHandler {

    @ExceptionHandler(Exception.class)
    public ResponseEntity<String> handleAllExceptions(Exception ex, WebRequest request) {
        log.error("Unhandled exception in testing support endpoint: {}", ex.getMessage(), ex);
        return ResponseEntity.internalServerError().body("An unexpected error occurred: " + ex.getMessage());
    }
} 