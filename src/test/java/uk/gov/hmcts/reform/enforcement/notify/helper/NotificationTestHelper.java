package uk.gov.hmcts.reform.enforcement.notify.helper;

import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

import uk.gov.service.notify.NotificationClientException;

public class NotificationTestHelper {

    /**
     * Creates a mocked NotificationClientException with the specified HTTP result code.
     */
    public static NotificationClientException createNotificationClientException(int httpStatusCode, String message) {
        NotificationClientException exception = mock(NotificationClientException.class);
        lenient().when(exception.getHttpResult()).thenReturn(httpStatusCode);
        lenient().when(exception.getMessage()).thenReturn(message);
        return exception;
    }
}
