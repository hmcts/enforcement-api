package uk.gov.hmcts.reform.enforcement.notify.task;

import com.github.kagkarlsson.scheduler.task.TaskInstance;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.hmcts.reform.enforcement.notify.config.NotificationErrorHandler;
import uk.gov.hmcts.reform.enforcement.notify.entities.CaseNotification;
import uk.gov.hmcts.reform.enforcement.notify.model.EmailState;
import uk.gov.hmcts.reform.enforcement.notify.repository.NotificationRepository;
import uk.gov.hmcts.reform.enforcement.notify.service.NotificationService;
import uk.gov.service.notify.NotificationClient;
import uk.gov.service.notify.NotificationClientException;
import uk.gov.service.notify.SendEmailResponse;

import java.time.Duration;
import java.util.HashMap;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class SendEmailTaskComponentTest {

    @Mock
    private NotificationService notificationService;

    @Mock
    private NotificationClient notificationClient;

    @Mock
    private NotificationErrorHandler errorHandler;

    @Mock
    private NotificationRepository notificationRepository;

    private SendEmailTaskComponent sendEmailTaskComponent;

    private TaskInstance<EmailState> taskInstance;
    private EmailState emailState;
    private CaseNotification caseNotification;
    private UUID dbNotificationId;

    @BeforeEach
    void setUp() {
        dbNotificationId = UUID.randomUUID();
        emailState = new EmailState(
            "test-task-id",
            "test@example.com",
            "template-id",
            new HashMap<>(),
            "reference",
            null,
            null,
            dbNotificationId
        );
        
        taskInstance = new TaskInstance<>(
            SendEmailTaskComponent.sendEmailTask.getTaskName(), 
            "test-id", 
            emailState
        );
        
        caseNotification = new CaseNotification();
        caseNotification.setNotificationId(dbNotificationId);
        
        sendEmailTaskComponent = new SendEmailTaskComponent(
            notificationService,
            notificationClient,
            errorHandler,
            notificationRepository,
            5,
            Duration.ofSeconds(300),
            Duration.ofSeconds(2)
        );
    }

    @Test
    void execute_ShouldSendEmailAndReturnCompletion_WhenSuccessful() throws Exception {
        when(notificationRepository.findById(dbNotificationId)).thenReturn(Optional.of(caseNotification));
        
        SendEmailResponse response = mock(SendEmailResponse.class);
        UUID notificationId = UUID.randomUUID();
        when(response.getNotificationId()).thenReturn(notificationId);
        
        when(notificationClient.sendEmail(
            anyString(), anyString(), any(), anyString()
        )).thenReturn(response);
        
        sendEmailTaskComponent.sendEmailTask().execute(taskInstance, null);
        
        verify(notificationService).updateNotificationAfterSending(
            eq(dbNotificationId), 
            eq(notificationId)
        );
    }

    @Test
    void execute_ShouldHandlePermanentFailure_WhenHttpError400() throws Exception {
        when(notificationRepository.findById(dbNotificationId)).thenReturn(Optional.of(caseNotification));
        
        NotificationClientException exception = mock(NotificationClientException.class);
        lenient().when(exception.getHttpResult()).thenReturn(400);
        when(notificationClient.sendEmail(
            anyString(), anyString(), any(), anyString()
        )).thenThrow(exception);
        
        sendEmailTaskComponent.sendEmailTask().execute(taskInstance, null);
        
        verify(notificationService).updateNotificationAfterFailure(
            eq(dbNotificationId), 
            any(NotificationClientException.class)
        );
    }

    @Test
    void execute_ShouldHandleTemporaryFailure_WhenNonPermanentFailure() throws Exception {
        when(notificationRepository.findById(dbNotificationId)).thenReturn(Optional.of(caseNotification));
        
        NotificationClientException exception = mock(NotificationClientException.class);
        lenient().when(exception.getHttpResult()).thenReturn(500);
        when(notificationClient.sendEmail(
            anyString(), anyString(), any(), anyString()
        )).thenThrow(exception);
        
        sendEmailTaskComponent.sendEmailTask().execute(taskInstance, null);
        
        verify(notificationService).updateNotificationAfterFailure(
            eq(dbNotificationId),
            any(NotificationClientException.class)
        );
        verify(errorHandler, never()).handleSendEmailException(any(), any(), any(), any());
    }

    @Test
    void isPermanentFailure_ShouldReturnTrue_ForHttp400() {
        NotificationClientException exception = mock(NotificationClientException.class);
        when(exception.getHttpResult()).thenReturn(400);
        
        boolean result = sendEmailTaskComponent.isPermanentFailure(exception);
        
        assertTrue(result);
    }

    @Test
    void isPermanentFailure_ShouldReturnTrue_ForHttp403() {
        NotificationClientException exception = mock(NotificationClientException.class);
        when(exception.getHttpResult()).thenReturn(403);
        
        boolean result = sendEmailTaskComponent.isPermanentFailure(exception);
        
        assertTrue(result);
    }

    @Test
    void isPermanentFailure_ShouldReturnFalse_ForOtherErrors() {
        NotificationClientException exception = mock(NotificationClientException.class);
        when(exception.getHttpResult()).thenReturn(500);
        
        boolean result = sendEmailTaskComponent.isPermanentFailure(exception);
        
        assertFalse(result);
    }
}
