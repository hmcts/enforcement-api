package uk.gov.hmcts.reform.enforcement.notify.exception;

public class TemporaryNotificationException extends NotificationException {
    public TemporaryNotificationException(String message, Exception cause) {
        super(message, cause);
    }
}
