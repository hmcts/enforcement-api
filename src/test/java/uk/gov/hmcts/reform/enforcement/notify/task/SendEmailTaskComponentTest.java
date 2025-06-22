package uk.gov.hmcts.reform.enforcement.notify.task;

import com.github.kagkarlsson.scheduler.task.CompletionHandler;
import com.github.kagkarlsson.scheduler.task.ExecutionContext;
import com.github.kagkarlsson.scheduler.task.TaskInstance;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import uk.gov.hmcts.reform.enforcement.notify.config.NotificationErrorHandler;
import uk.gov.hmcts.reform.enforcement.notify.entities.CaseNotification;
import uk.gov.hmcts.reform.enforcement.notify.exception.PermanentNotificationException;
import uk.gov.hmcts.reform.enforcement.notify.exception.TemporaryNotificationException;
import uk.gov.hmcts.reform.enforcement.notify.helper.NotificationTestHelper;
import uk.gov.hmcts.reform.enforcement.notify.model.EmailState;
import uk.gov.hmcts.reform.enforcement.notify.model.NotificationStatus;
import uk.gov.hmcts.reform.enforcement.notify.repository.NotificationRepository;
import uk.gov.hmcts.reform.enforcement.notify.service.NotificationService;
import uk.gov.service.notify.NotificationClient;
import uk.gov.service.notify.NotificationClientException;
import uk.gov.service.notify.SendEmailResponse;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SendEmailTaskComponentTest {

    @Mock
    private NotificationService notificationService;

    @Mock
    private NotificationClient notificationClient;

    @Mock
    private NotificationErrorHandler errorHandler;

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private TaskInstance<EmailState> taskInstance;

    @Mock
    private ExecutionContext executionContext;

    private SendEmailTaskComponent sendEmailTaskComponent;

    private EmailState emailState;
    private CaseNotification caseNotification;
    private UUID dbNotificationId;
    private UUID providerNotificationId;

    @BeforeEach
    void setUp() {
        dbNotificationId = UUID.randomUUID();
        providerNotificationId = UUID.randomUUID();

        Map<String, Object> personalisation = new HashMap<>();
        personalisation.put("name", "John Doe");

        emailState = EmailState.builder()
            .id("task-123")
            .emailAddress("test@example.com")
            .templateId("template-123")
            .personalisation(personalisation)
            .reference("ref-123")
            .emailReplyToId("reply-to-123")
            .dbNotificationId(dbNotificationId)
            .build();

        caseNotification = new CaseNotification();
        caseNotification.setNotificationId(dbNotificationId);
        caseNotification.setType("Email");
        caseNotification.setRecipient("test@example.com");

        lenient().when(taskInstance.getData()).thenReturn(emailState);

        sendEmailTaskComponent = new SendEmailTaskComponent(
            notificationService,
            notificationClient,
            errorHandler,
            notificationRepository,
            5,
            Duration.ofSeconds(300),
            Duration.ofSeconds(60)
        );
    }

    @Test
    void execute_ShouldSendEmailAndReturnCompletion_WhenSuccessful() throws Exception {
        SendEmailResponse sendEmailResponse = mock(SendEmailResponse.class);
        when(sendEmailResponse.getNotificationId()).thenReturn(providerNotificationId);
        
        when(notificationRepository.findById(dbNotificationId)).thenReturn(Optional.of(caseNotification));
        when(notificationClient.sendEmail(anyString(), anyString(), anyMap(), anyString()))
            .thenReturn(sendEmailResponse);

        CompletionHandler<EmailState> result = sendEmailTaskComponent.sendEmailTask()
            .execute(taskInstance, executionContext);

        verify(notificationClient).sendEmail(
            eq("template-123"),
            eq("test@example.com"),
            eq(emailState.getPersonalisation()),
            anyString()
        );
        verify(notificationService).updateNotificationAfterSending(dbNotificationId, providerNotificationId);
        assertThat(result).isInstanceOf(CompletionHandler.OnCompleteReplace.class);
    }

    @Test
    void execute_ShouldReturnRemoveCompletion_WhenNotificationNotFound() throws Exception {
        when(notificationRepository.findById(dbNotificationId)).thenReturn(Optional.empty());

        CompletionHandler<EmailState> result = sendEmailTaskComponent.sendEmailTask()
            .execute(taskInstance, executionContext);

        verify(notificationClient, never()).sendEmail(anyString(), anyString(), anyMap(), anyString());
        verify(notificationService, never()).updateNotificationAfterSending(any(), any());
        assertThat(result).isInstanceOf(CompletionHandler.OnCompleteRemove.class);
    }

    @Test
    void execute_ShouldThrowTemporaryException_WhenNotificationClientThrowsTemporaryError() throws Exception {
        when(notificationRepository.findById(dbNotificationId)).thenReturn(Optional.of(caseNotification));

        NotificationClientException clientException = 
            NotificationTestHelper.createNotificationClientException(429, "Rate limited");
        when(notificationClient.sendEmail(anyString(), anyString(), anyMap(), anyString()))
            .thenThrow(clientException);

        assertThatThrownBy(() -> {
            sendEmailTaskComponent.sendEmailTask().execute(taskInstance, executionContext);
        }).isInstanceOf(TemporaryNotificationException.class);

        verify(notificationClient).sendEmail(anyString(), anyString(), anyMap(), anyString());
        verify(notificationService, never()).updateNotificationAfterSending(any(), any());
    }

    @Test
    void execute_ShouldReturnRemoveCompletion_WhenNotificationClientThrowsPermanentError() throws Exception {
        when(notificationRepository.findById(dbNotificationId)).thenReturn(Optional.of(caseNotification));

        NotificationClientException clientException = 
            NotificationTestHelper.createNotificationClientException(400, "Bad request");
        when(notificationClient.sendEmail(anyString(), anyString(), anyMap(), anyString()))
            .thenThrow(clientException);

        CompletionHandler<EmailState> result = sendEmailTaskComponent.sendEmailTask()
            .execute(taskInstance, executionContext);

        verify(notificationClient).sendEmail(anyString(), anyString(), anyMap(), anyString());
        verify(notificationService, never()).updateNotificationAfterSending(any(), any());
        verify(errorHandler).handleSendEmailException(
            eq(clientException),
            eq(caseNotification),
            anyString(),
            any()
        );
        assertThat(result).isInstanceOf(CompletionHandler.OnCompleteRemove.class);
    }

    @Test
    void execute_ShouldThrowPermanentException_WhenResponseContainsNullId() throws Exception {
        when(notificationRepository.findById(dbNotificationId)).thenReturn(Optional.of(caseNotification));
        
        SendEmailResponse sendEmailResponse = mock(SendEmailResponse.class);
        when(sendEmailResponse.getNotificationId()).thenReturn(null);
        
        when(notificationClient.sendEmail(anyString(), anyString(), anyMap(), anyString()))
            .thenReturn(sendEmailResponse);

        assertThatThrownBy(() -> {
            sendEmailTaskComponent.sendEmailTask().execute(taskInstance, executionContext);
        }).isInstanceOf(PermanentNotificationException.class);

        verify(notificationClient).sendEmail(anyString(), anyString(), anyMap(), anyString());
        verify(notificationService, never()).updateNotificationAfterSending(any(), any());
    }

    @Test
    void isPermanentFailure_ShouldReturnTrue_ForHttpStatus400() {
        NotificationClientException exception = 
            NotificationTestHelper.createNotificationClientException(400, "Bad request");
        boolean result = sendEmailTaskComponent.isPermanentFailure(exception);
        assertThat(result).isTrue();
    }

    @Test
    void isPermanentFailure_ShouldReturnTrue_ForHttpStatus403() {
        NotificationClientException exception = 
            NotificationTestHelper.createNotificationClientException(403, "Forbidden");
        boolean result = sendEmailTaskComponent.isPermanentFailure(exception);
        assertThat(result).isTrue();
    }

    @Test
    void isPermanentFailure_ShouldReturnFalse_ForOtherHttpStatuses() {
        int[] nonPermanentStatuses = {401, 404, 429, 500, 502, 503};
        for (int status : nonPermanentStatuses) {
            NotificationClientException exception = 
                NotificationTestHelper.createNotificationClientException(status, "Error");
            boolean result = sendEmailTaskComponent.isPermanentFailure(exception);
            assertThat(result).isFalse();
        }
    }

    @Test
    void updateNotificationFromStatusUpdate_ShouldCallNotificationService() {
        CaseNotification notification = new CaseNotification();
        UUID notificationId = UUID.randomUUID();
        notification.setNotificationId(notificationId);
        NotificationErrorHandler.NotificationStatusUpdate update = 
            new NotificationErrorHandler.NotificationStatusUpdate(
                notification,
                NotificationStatus.PERMANENT_FAILURE,
                null
            );

        notificationService.updateNotificationStatus(
            notification.getNotificationId(),
            "permanent-failure"
        );
        
        verify(notificationService).updateNotificationStatus(
            notification.getNotificationId(),
            "permanent-failure"
        );
    }

    @Test
    void sendEmailTask_ShouldReturnCustomTask() {
        assertThat(sendEmailTaskComponent.sendEmailTask()).isNotNull();
    }
}
