package uk.gov.hmcts.reform.enforcement.notify.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import uk.gov.hmcts.reform.enforcement.notify.config.NotificationErrorHandler.NotificationStatusUpdate;
import uk.gov.hmcts.reform.enforcement.notify.entities.CaseNotification;
import uk.gov.hmcts.reform.enforcement.notify.exception.NotificationException;
import uk.gov.hmcts.reform.enforcement.notify.exception.TemporaryNotificationException;
import uk.gov.hmcts.reform.enforcement.notify.helper.NotificationTestHelper;
import uk.gov.hmcts.reform.enforcement.notify.model.NotificationStatus;
import uk.gov.service.notify.NotificationClientException;

import java.util.UUID;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class NotificationErrorHandlerTest {

    private NotificationErrorHandler errorHandler;

    @Mock
    private Consumer<NotificationStatusUpdate> statusUpdater;

    private CaseNotification caseNotification;
    private String referenceId;

    @BeforeEach
    void setUp() {
        errorHandler = new NotificationErrorHandler();
        caseNotification = new CaseNotification();
        caseNotification.setNotificationId(UUID.randomUUID());
        referenceId = UUID.randomUUID().toString();
    }

    @Test
    void handleSendEmailException_ShouldUpdateStatusToPermanentFailure_WhenHttpStatus400() {
        NotificationClientException exception = 
            NotificationTestHelper.createNotificationClientException(400, "Bad request");

        errorHandler.handleSendEmailException(exception, caseNotification, referenceId, statusUpdater);
        
        ArgumentCaptor<NotificationStatusUpdate> captor = ArgumentCaptor.forClass(NotificationStatusUpdate.class);
        verify(statusUpdater).accept(captor.capture());
        assertThat(captor.getValue().status()).isEqualTo(NotificationStatus.PERMANENT_FAILURE);
        assertThat(captor.getValue().notification()).isEqualTo(caseNotification);
    }

    @Test
    void handleSendEmailException_ShouldUpdateStatusToPermanentFailure_WhenHttpStatus403() {
        NotificationClientException exception = 
            NotificationTestHelper.createNotificationClientException(403, "Forbidden");

        errorHandler.handleSendEmailException(exception, caseNotification, referenceId, statusUpdater);
        
        ArgumentCaptor<NotificationStatusUpdate> captor = ArgumentCaptor.forClass(NotificationStatusUpdate.class);
        verify(statusUpdater).accept(captor.capture());
        assertThat(captor.getValue().status()).isEqualTo(NotificationStatus.PERMANENT_FAILURE);
    }

    @Test
    void handleSendEmailException_ShouldUpdateStatusToTemporaryFailureAndThrow_WhenHttpStatus429() {
        NotificationClientException exception = 
            NotificationTestHelper.createNotificationClientException(429, "Too many requests");

        assertThatThrownBy(() -> {
            errorHandler.handleSendEmailException(exception, caseNotification, referenceId, statusUpdater);
        })
            .isInstanceOf(TemporaryNotificationException.class)
            .hasMessage("Email temporarily failed to send.")
            .hasCause(exception);

        ArgumentCaptor<NotificationStatusUpdate> captor = ArgumentCaptor.forClass(NotificationStatusUpdate.class);
        verify(statusUpdater).accept(captor.capture());
        assertThat(captor.getValue().status()).isEqualTo(NotificationStatus.TEMPORARY_FAILURE);
    }

    @Test
    void handleSendEmailException_ShouldUpdateStatusToTemporaryFailureAndThrow_WhenHttpStatus500() {
        NotificationClientException exception = 
            NotificationTestHelper.createNotificationClientException(500, "Server error");

        assertThatThrownBy(() -> {
            errorHandler.handleSendEmailException(exception, caseNotification, referenceId, statusUpdater);
        })
            .isInstanceOf(TemporaryNotificationException.class)
            .hasMessage("Email temporarily failed to send.")
            .hasCause(exception);

        ArgumentCaptor<NotificationStatusUpdate> captor = ArgumentCaptor.forClass(NotificationStatusUpdate.class);
        verify(statusUpdater).accept(captor.capture());
        assertThat(captor.getValue().status()).isEqualTo(NotificationStatus.TEMPORARY_FAILURE);
    }

    @Test
    void handleSendEmailException_ShouldUpdateStatusToTechnicalFailureAndThrow_WhenHttpStatusOther() {
        NotificationClientException exception = 
            NotificationTestHelper.createNotificationClientException(503, "Unknown error");

        assertThatThrownBy(() -> {
            errorHandler.handleSendEmailException(exception, caseNotification, referenceId, statusUpdater);
        })
            .isInstanceOf(NotificationException.class)
            .hasMessage("Email failed to send, please try again.")
            .hasCause(exception);

        ArgumentCaptor<NotificationStatusUpdate> captor = ArgumentCaptor.forClass(NotificationStatusUpdate.class);
        verify(statusUpdater).accept(captor.capture());
        assertThat(captor.getValue().status()).isEqualTo(NotificationStatus.TECHNICAL_FAILURE);
    }

    @Test
    void handleFetchException_ShouldThrowNotificationException() {
        NotificationClientException exception = 
            NotificationTestHelper.createNotificationClientException(404, "Fetch error");
        String notificationId = UUID.randomUUID().toString();

        assertThatThrownBy(() -> {
            errorHandler.handleFetchException(exception, notificationId);
        })
            .isInstanceOf(NotificationException.class)
            .hasMessage("Failed to fetch notification, please try again.")
            .hasCause(exception);
    }
}
