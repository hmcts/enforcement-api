package uk.gov.hmcts.reform.enforcement.notify.exception;

public class PermanentNotificationException extends NotificationException {
    public PermanentNotificationException(String message, Exception cause) {
        super(message, cause);
    }
}
