package uk.gov.hmcts.reform.enforcement.notify.exception;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PermanentNotificationExceptionTest {

    @Test
    void constructor_ShouldSetMessageAndCause() {
        String message = "Test error message";
        Exception cause = new RuntimeException("Test cause");

        PermanentNotificationException exception = new PermanentNotificationException(message, cause);

        assertThat(exception.getMessage()).isEqualTo(message);
        assertThat(exception.getCause()).isEqualTo(cause);
    }
    
    @Test
    void exception_ShouldExtendNotificationException() {
        PermanentNotificationException exception = new PermanentNotificationException("Test", new RuntimeException());
        
        assertThat(exception).isInstanceOf(NotificationException.class);
    }
}
