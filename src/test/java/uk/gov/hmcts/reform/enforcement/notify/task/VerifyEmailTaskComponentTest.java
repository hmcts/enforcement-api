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
import uk.gov.hmcts.reform.enforcement.notify.exception.NotificationException;
import uk.gov.hmcts.reform.enforcement.notify.helper.NotificationTestHelper;
import uk.gov.hmcts.reform.enforcement.notify.model.EmailState;
import uk.gov.hmcts.reform.enforcement.notify.model.NotificationStatus;
import uk.gov.hmcts.reform.enforcement.notify.service.NotificationService;
import uk.gov.service.notify.Notification;
import uk.gov.service.notify.NotificationClient;
import uk.gov.service.notify.NotificationClientException;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class VerifyEmailTaskComponentTest {

    @Mock
    private NotificationService notificationService;

    @Mock
    private NotificationClient notificationClient;

    @Mock
    private NotificationErrorHandler errorHandler;

    @Mock
    private TaskInstance<EmailState> taskInstance;

    @Mock
    private ExecutionContext executionContext;

    private VerifyEmailTaskComponent verifyEmailTaskComponent;

    private EmailState emailState;
    private UUID dbNotificationId;
    private String notificationId;
    private Map<String, Object> personalisation;

    @BeforeEach
    void setUp() {
        dbNotificationId = UUID.randomUUID();
        notificationId = UUID.randomUUID().toString();

        personalisation = new HashMap<>();
        personalisation.put("name", "John Doe");

        emailState = EmailState.builder()
            .id("task-123")
            .emailAddress("test@example.com")
            .templateId("template-123")
            .personalisation(personalisation)
            .reference("ref-123")
            .emailReplyToId("reply-to-123")
            .notificationId(notificationId)
            .dbNotificationId(dbNotificationId)
            .build();

        lenient().when(taskInstance.getData()).thenReturn(emailState);

        verifyEmailTaskComponent = new VerifyEmailTaskComponent(
            notificationService,
            notificationClient,
            errorHandler,
            5,
            Duration.ofSeconds(3600)
        );
    }

    @Test
    void execute_ShouldUpdateStatusToDelivered_WhenNotificationStatusIsDelivered() throws Exception {
        Notification notification = mock(Notification.class);
        when(notificationClient.getNotificationById(notificationId)).thenReturn(notification);
        when(notification.getStatus()).thenReturn(NotificationStatus.DELIVERED.toString());

        CompletionHandler<EmailState> result = verifyEmailTaskComponent.verifyEmailTask()
            .execute(taskInstance, executionContext);

        verify(notificationClient).getNotificationById(notificationId);
        verify(notificationService).updateNotificationStatus(
            dbNotificationId, 
            NotificationStatus.DELIVERED.toString()
        );
        assertThat(result).isInstanceOf(CompletionHandler.OnCompleteRemove.class);
    }

    @Test
    void execute_ShouldUpdateStatusToPermanentFailure_WhenNotificationStatusIsNotDelivered() throws Exception {
        Notification notification = mock(Notification.class);
        when(notificationClient.getNotificationById(notificationId)).thenReturn(notification);
        when(notification.getStatus()).thenReturn("failed");

        CompletionHandler<EmailState> result = verifyEmailTaskComponent.verifyEmailTask()
            .execute(taskInstance, executionContext);

        verify(notificationClient).getNotificationById(notificationId);
        verify(notificationService).updateNotificationStatus(
            dbNotificationId, 
            NotificationStatus.PERMANENT_FAILURE.toString()
        );
        assertThat(result).isInstanceOf(CompletionHandler.OnCompleteRemove.class);
    }

    @Test
    void execute_ShouldHandleNotificationClientException() throws Exception {
        NotificationClientException clientException = 
            NotificationTestHelper.createNotificationClientException(404, "Not found");
        when(notificationClient.getNotificationById(notificationId)).thenThrow(clientException);

        CompletionHandler<EmailState> result = verifyEmailTaskComponent.verifyEmailTask()
            .execute(taskInstance, executionContext);

        verify(notificationClient).getNotificationById(notificationId);
        verify(errorHandler).handleFetchException(
            eq(clientException), 
            eq(notificationId), 
            eq(dbNotificationId)
        );
        assertThat(result).isInstanceOf(CompletionHandler.OnCompleteRemove.class);
    }

    @Test
    void execute_ShouldBeCaseInsensitiveForDeliveredStatus() throws Exception {
        Notification notification = mock(Notification.class);
        when(notificationClient.getNotificationById(notificationId)).thenReturn(notification);
        when(notification.getStatus()).thenReturn("DELIVERED");

        CompletionHandler<EmailState> result = verifyEmailTaskComponent.verifyEmailTask()
            .execute(taskInstance, executionContext);

        verify(notificationClient).getNotificationById(notificationId);
        verify(notificationService).updateNotificationStatus(dbNotificationId, "DELIVERED");
        assertThat(result).isInstanceOf(CompletionHandler.OnCompleteRemove.class);
    }

    @Test
    void execute_ShouldHandleTemporaryFailureStatus() throws Exception {
        Notification notification = mock(Notification.class);
        when(notificationClient.getNotificationById(notificationId)).thenReturn(notification);
        when(notification.getStatus()).thenReturn("temporary-failure");

        CompletionHandler<EmailState> result = verifyEmailTaskComponent.verifyEmailTask()
            .execute(taskInstance, executionContext);

        verify(notificationClient).getNotificationById(notificationId);
        verify(notificationService).updateNotificationStatus(
            dbNotificationId, 
            NotificationStatus.PERMANENT_FAILURE.toString()
        );
        assertThat(result).isInstanceOf(CompletionHandler.OnCompleteRemove.class);
    }

    @Test
    void execute_ShouldHandleOtherNotificationClientExceptions() throws Exception {
        NotificationClientException clientException = 
            NotificationTestHelper.createNotificationClientException(500, "Server error");
        when(notificationClient.getNotificationById(notificationId)).thenThrow(clientException);

        CompletionHandler<EmailState> result = verifyEmailTaskComponent.verifyEmailTask()
            .execute(taskInstance, executionContext);

        verify(notificationClient).getNotificationById(notificationId);
        verify(errorHandler).handleFetchException(
            eq(clientException), 
            eq(notificationId), 
            eq(dbNotificationId)
        );
        assertThat(result).isInstanceOf(CompletionHandler.OnCompleteRemove.class);
    }

    @Test
    void verifyEmailTask_ShouldReturnCustomTask() {
        assertThat(verifyEmailTaskComponent.verifyEmailTask()).isNotNull();
    }
    
    @Test
    void execute_ShouldHandleExceptionFromErrorHandler() throws Exception {
        NotificationClientException clientException = 
            NotificationTestHelper.createNotificationClientException(404, "Not found");
        when(notificationClient.getNotificationById(notificationId)).thenThrow(clientException);
        
        NotificationException handlerException = new NotificationException(
            "Error handling notification", 
            new RuntimeException()
        );
        doThrow(handlerException).when(errorHandler).handleFetchException(
            eq(clientException), 
            eq(notificationId), 
            eq(dbNotificationId)
        );

        CompletionHandler<EmailState> result = verifyEmailTaskComponent.verifyEmailTask()
            .execute(taskInstance, executionContext);

        verify(notificationClient).getNotificationById(notificationId);
        verify(errorHandler).handleFetchException(
            eq(clientException), 
            eq(notificationId), 
            eq(dbNotificationId)
        );
        assertThat(result).isInstanceOf(CompletionHandler.OnCompleteRemove.class);
    }
    
    @Test
    void execute_ShouldLogStatusOutcomes() throws Exception {
        Notification deliveredNotification = mock(Notification.class);
        when(notificationClient.getNotificationById(notificationId)).thenReturn(deliveredNotification);
        when(deliveredNotification.getStatus()).thenReturn(NotificationStatus.DELIVERED.toString());

        verifyEmailTaskComponent.verifyEmailTask().execute(taskInstance, executionContext);
        
        Notification failedNotification = mock(Notification.class);
        when(notificationClient.getNotificationById(notificationId)).thenReturn(failedNotification);
        when(failedNotification.getStatus()).thenReturn("failed");
        
        verifyEmailTaskComponent.verifyEmailTask().execute(taskInstance, executionContext);
        
        verify(notificationClient, times(2)).getNotificationById(notificationId);
        verify(notificationService).updateNotificationStatus(
            dbNotificationId, 
            NotificationStatus.DELIVERED.toString()
        );
        verify(notificationService).updateNotificationStatus(
            dbNotificationId, 
            NotificationStatus.PERMANENT_FAILURE.toString()
        );
    }
    
    @Test
    void taskDescriptor_ShouldHaveCorrectNameAndType() {
        assertThat(VerifyEmailTaskComponent.verifyEmailTask.getTaskName()).isEqualTo("verify-email-task");
        assertThat(VerifyEmailTaskComponent.verifyEmailTask.getDataClass()).isEqualTo(EmailState.class);
    }
    
    @Test
    void execute_ShouldIgnoreNullDbNotificationId() throws Exception {
        EmailState stateWithNullDbId = emailState.toBuilder()
            .dbNotificationId(null)
            .build();
        when(taskInstance.getData()).thenReturn(stateWithNullDbId);
        
        NotificationClientException clientException = 
            NotificationTestHelper.createNotificationClientException(404, "Not found");
        when(notificationClient.getNotificationById(notificationId)).thenThrow(clientException);

        CompletionHandler<EmailState> result = verifyEmailTaskComponent.verifyEmailTask()
            .execute(taskInstance, executionContext);

        verify(notificationClient).getNotificationById(notificationId);
        verify(errorHandler).handleFetchException(
            eq(clientException), 
            eq(notificationId), 
            eq(null)
        );
        verify(notificationService, never()).updateNotificationStatus(any(), any());
        assertThat(result).isInstanceOf(CompletionHandler.OnCompleteRemove.class);
    }
}
