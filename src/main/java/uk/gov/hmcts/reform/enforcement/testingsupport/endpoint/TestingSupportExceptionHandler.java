package uk.gov.hmcts.reform.enforcement.testingsupport.endpoint;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import uk.gov.hmcts.reform.enforcement.notify.exception.NotificationException;

@ControllerAdvice(basePackages = "uk.gov.hmcts.reform.enforcement.testingsupport")
public class TestingSupportExceptionHandler {

    @ExceptionHandler(NotificationException.class)
    public ResponseEntity<Error> handleNotificationException(NotificationException ex) {
        return ResponseEntity
            .status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(new Error(ex.getMessage()));
    }

    public record Error(String message) {}
}
