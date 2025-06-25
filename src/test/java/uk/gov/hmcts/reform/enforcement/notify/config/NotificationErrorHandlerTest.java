package uk.gov.hmcts.reform.enforcement.notify.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.hmcts.reform.enforcement.notify.entities.CaseNotification;
import uk.gov.hmcts.reform.enforcement.notify.service.NotificationService;
import uk.gov.service.notify.NotificationClientException;

@ExtendWith(MockitoExtension.class)
class NotificationErrorHandlerTest {

    private NotificationErrorHandler errorHandler;

    @Mock
    private NotificationService notificationService;

    @BeforeEach
    void setUp() {
        errorHandler = new NotificationErrorHandler(notificationService);
    }

    @Test
    void handleSendEmailException_ShouldUpdateStatusToSubmitted_WhenHttpStatus400() {
        NotificationClientException exception = mock(NotificationClientException.class);
        when(exception.getHttpResult()).thenReturn(400);
        when(exception.getMessage()).thenReturn("Bad Request");

        CaseNotification caseNotification = mock(CaseNotification.class);
        AtomicReference<String> updatedStatus = new AtomicReference<>();

        errorHandler.handleSendEmailException(
            exception,
            caseNotification,
            "test-reference",
            statusUpdate -> updatedStatus.set(statusUpdate.status().toString())
        );

        assertEquals("submitted", updatedStatus.get());
    }

    @Test
    void handleSendEmailException_ShouldUpdateStatusToTemporaryFailure_WhenHttpStatus429() {
        NotificationClientException exception = mock(NotificationClientException.class);
        when(exception.getHttpResult()).thenReturn(429);
        when(exception.getMessage()).thenReturn("Too Many Requests");

        CaseNotification caseNotification = mock(CaseNotification.class);
        AtomicReference<String> updatedStatus = new AtomicReference<>();

        try {
            errorHandler.handleSendEmailException(
                exception,
                caseNotification,
                "test-reference",
                statusUpdate -> updatedStatus.set(statusUpdate.status().toString())
            );
        } catch (Exception e) {
            // Expected exception
        }

        assertEquals("temporary-failure", updatedStatus.get());
    }

    @Test
    void handleSendEmailException_ShouldUpdateStatusToTechnicalFailure_WhenHttpStatus401() {
        NotificationClientException exception = mock(NotificationClientException.class);
        when(exception.getHttpResult()).thenReturn(401);
        when(exception.getMessage()).thenReturn("Unauthorized");

        CaseNotification caseNotification = mock(CaseNotification.class);
        AtomicReference<String> updatedStatus = new AtomicReference<>();

        try {
            errorHandler.handleSendEmailException(
                exception,
                caseNotification,
                "test-reference",
                statusUpdate -> updatedStatus.set(statusUpdate.status().toString())
            );
        } catch (Exception e) {
            // Expected exception
        }

        assertEquals("technical-failure", updatedStatus.get());
    }

    @Test
    void handleFetchException_ShouldThrowNotificationException() {
        NotificationClientException exception = mock(NotificationClientException.class);
        when(exception.getHttpResult()).thenReturn(500);
        when(exception.getMessage()).thenReturn("Internal Server Error");

        try {
            errorHandler.handleFetchException(exception, "notification-id");
        } catch (Exception e) {
            assertEquals("Failed to fetch notification, please try again.", e.getMessage());
        }
    }

    @Test
    void handleFetchException_ShouldUpdateStatusToPermanentFailure_ForHttpStatus404() {
        NotificationClientException exception = mock(NotificationClientException.class);
        when(exception.getHttpResult()).thenReturn(404);
        when(exception.getMessage()).thenReturn("Not Found");

        UUID dbNotificationId = UUID.randomUUID();

        try {
            errorHandler.handleFetchException(exception, "notification-id", dbNotificationId);
        } catch (Exception e) {
            assertEquals("Failed to fetch notification, please try again.", e.getMessage());
        }
    }
}
