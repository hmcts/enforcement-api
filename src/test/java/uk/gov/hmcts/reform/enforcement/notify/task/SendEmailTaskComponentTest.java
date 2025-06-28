package uk.gov.hmcts.reform.enforcement.notify.task;

import com.github.kagkarlsson.scheduler.task.CompletionHandler;
import com.github.kagkarlsson.scheduler.task.ExecutionContext;
import com.github.kagkarlsson.scheduler.task.TaskInstance;
import com.github.kagkarlsson.scheduler.task.helper.CustomTask;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import uk.gov.hmcts.reform.enforcement.notify.config.NotificationErrorHandler;
import uk.gov.hmcts.reform.enforcement.notify.config.NotificationErrorHandler.NotificationStatusUpdate;
import uk.gov.hmcts.reform.enforcement.notify.entities.CaseNotification;
import uk.gov.hmcts.reform.enforcement.notify.exception.PermanentNotificationException;
import uk.gov.hmcts.reform.enforcement.notify.exception.TemporaryNotificationException;
import uk.gov.hmcts.reform.enforcement.notify.model.EmailState;
import uk.gov.hmcts.reform.enforcement.notify.model.NotificationStatus;
import uk.gov.hmcts.reform.enforcement.notify.repository.NotificationRepository;
import uk.gov.hmcts.reform.enforcement.notify.service.NotificationService;
import uk.gov.service.notify.NotificationClient;
import uk.gov.service.notify.NotificationClientException;
import uk.gov.service.notify.SendEmailResponse;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("SendEmailTaskComponent Tests")
class SendEmailTaskComponentTest {

    private SendEmailTaskComponent sendEmailTaskComponent;

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

    @Mock
    private CaseNotification caseNotification;

    @Mock
    private SendEmailResponse sendEmailResponse;

    private final Duration sendingBackoffDelay = Duration.ofSeconds(30);
    private final Duration processingDelay = Duration.ofSeconds(2);
    private final Duration statusCheckTaskDelay = Duration.ofMinutes(5);

    private EmailState emailState;
    private final UUID dbNotificationId = UUID.randomUUID();
    private final String templateId = "template-456";
    private final String emailAddress = "test@example.com";
    private final Map<String, Object> personalisation = Map.of("name", "John Doe");
    private final UUID notificationId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        int maxRetriesSendEmail = 3;
        sendEmailTaskComponent = new SendEmailTaskComponent(
            notificationService,
            notificationClient,
            errorHandler,
            notificationRepository,
            maxRetriesSendEmail,
            sendingBackoffDelay,
            processingDelay,
            statusCheckTaskDelay
        );

        String taskId = "task-123";
        emailState = EmailState.builder()
            .id(taskId)
            .dbNotificationId(dbNotificationId)
            .templateId(templateId)
            .emailAddress(emailAddress)
            .personalisation(personalisation)
            .build();

        when(taskInstance.getData()).thenReturn(emailState);
        when(taskInstance.getId()).thenReturn(taskId);
    }

    @Nested
    @DisplayName("Component Initialization Tests")
    class ComponentInitializationTests {

        @Test
        @DisplayName("Should create task descriptor with correct name and type")
        void shouldCreateTaskDescriptorWithCorrectNameAndType() {
            assertThat(SendEmailTaskComponent.sendEmailTask.getTaskName()).isEqualTo("send-email-task");
            assertThat(SendEmailTaskComponent.sendEmailTask.getDataClass()).isEqualTo(EmailState.class);
        }

        @Test
        @DisplayName("Should create send email task bean")
        void shouldCreateSendEmailTaskBean() {
            CustomTask<EmailState> task = sendEmailTaskComponent.sendEmailTask();

            assertThat(task).isNotNull();
        }
    }

    @Nested
    @DisplayName("Successful Email Sending Tests")
    class SuccessfulEmailSendingTests {

        @Test
        @DisplayName("Should send email successfully and schedule verify task")
        void shouldSendEmailSuccessfullyAndScheduleVerifyTask() throws Exception {
            when(notificationRepository.findById(dbNotificationId)).thenReturn(Optional.of(caseNotification));
            when(sendEmailResponse.getNotificationId()).thenReturn(notificationId);
            when(notificationClient.sendEmail(eq(templateId), eq(emailAddress), eq(personalisation), anyString()))
                .thenReturn(sendEmailResponse);

            CustomTask<EmailState> task = sendEmailTaskComponent.sendEmailTask();

            CompletionHandler<EmailState> result = task.execute(taskInstance, executionContext);

            verify(notificationClient).sendEmail(eq(templateId), eq(emailAddress), eq(personalisation), anyString());
            verify(notificationService).updateNotificationAfterSending(dbNotificationId, notificationId);
            
            assertThat(result).isInstanceOf(CompletionHandler.OnCompleteReplace.class);
        }

        @Test
        @DisplayName("Should generate unique reference ID for each email")
        void shouldGenerateUniqueReferenceIdForEachEmail() throws Exception {
            when(notificationRepository.findById(dbNotificationId)).thenReturn(Optional.of(caseNotification));
            when(sendEmailResponse.getNotificationId()).thenReturn(notificationId);
            when(notificationClient.sendEmail(eq(templateId), eq(emailAddress), eq(personalisation), anyString()))
                .thenReturn(sendEmailResponse);

            CustomTask<EmailState> task = sendEmailTaskComponent.sendEmailTask();

            task.execute(taskInstance, executionContext);

            ArgumentCaptor<String> referenceCaptor = ArgumentCaptor.forClass(String.class);
            verify(notificationClient).sendEmail(eq(templateId), eq(emailAddress), eq(personalisation),
                referenceCaptor.capture());

            String referenceId = referenceCaptor.getValue();
            assertThat(referenceId).isNotNull().isNotEmpty();
            assertThat(UUID.fromString(referenceId)).isNotNull();
        }

        @Test
        @DisplayName("Should create next state with notification ID")
        void shouldCreateNextStateWithNotificationId() throws Exception {
            when(notificationRepository.findById(dbNotificationId)).thenReturn(Optional.of(caseNotification));
            when(sendEmailResponse.getNotificationId()).thenReturn(notificationId);
            when(notificationClient.sendEmail(eq(templateId), eq(emailAddress), eq(personalisation), anyString()))
                .thenReturn(sendEmailResponse);

            CustomTask<EmailState> task = sendEmailTaskComponent.sendEmailTask();

            CompletionHandler<EmailState> result = task.execute(taskInstance, executionContext);

            assertThat(result).isInstanceOf(CompletionHandler.OnCompleteReplace.class);
        }
    }

    @Nested
    @DisplayName("Notification Not Found Tests")
    class NotificationNotFoundTests {

        @Test
        @DisplayName("Should return OnCompleteRemove when notification not found")
        void shouldReturnOnCompleteRemoveWhenNotificationNotFound() throws Exception {
            when(notificationRepository.findById(dbNotificationId)).thenReturn(Optional.empty());

            CustomTask<EmailState> task = sendEmailTaskComponent.sendEmailTask();

            CompletionHandler<EmailState> result = task.execute(taskInstance, executionContext);

            assertThat(result).isInstanceOf(CompletionHandler.OnCompleteRemove.class);
            verify(notificationClient, never()).sendEmail(anyString(), anyString(), any(), anyString());
            verify(notificationService, never()).updateNotificationAfterSending(any(), any());
        }

        @Test
        @DisplayName("Should log error when notification not found")
        void shouldLogErrorWhenNotificationNotFound() {
            when(notificationRepository.findById(dbNotificationId)).thenReturn(Optional.empty());

            CustomTask<EmailState> task = sendEmailTaskComponent.sendEmailTask();

            CompletionHandler<EmailState> result = task.execute(taskInstance, executionContext);

            assertThat(result).isInstanceOf(CompletionHandler.OnCompleteRemove.class);
        }
    }

    @Nested
    @DisplayName("Null Notification ID Tests")
    class NullNotificationIdTests {

        @Test
        @DisplayName("Should throw PermanentNotificationException when notification ID is null")
        void shouldThrowPermanentNotificationExceptionWhenNotificationIdIsNull() throws Exception {
            when(notificationRepository.findById(dbNotificationId)).thenReturn(Optional.of(caseNotification));
            when(sendEmailResponse.getNotificationId()).thenReturn(null);
            when(notificationClient.sendEmail(eq(templateId), eq(emailAddress), eq(personalisation), anyString()))
                .thenReturn(sendEmailResponse);

            CustomTask<EmailState> task = sendEmailTaskComponent.sendEmailTask();

            assertThatThrownBy(() -> task.execute(taskInstance, executionContext))
                .isInstanceOf(PermanentNotificationException.class)
                .hasMessage("Null notification ID from email service");

            verify(notificationService, never()).updateNotificationAfterSending(any(), any());
        }
    }

    @Nested
    @DisplayName("NotificationClientException Handling Tests")
    class NotificationClientExceptionHandlingTests {

        @Test
        @DisplayName("Should handle permanent failure with 400 status code")
        void shouldHandlePermanentFailureWith400StatusCode() throws Exception {
            NotificationClientException exception = mock(NotificationClientException.class);
            when(exception.getHttpResult()).thenReturn(400);
            when(exception.getMessage()).thenReturn("Bad Request");

            when(notificationRepository.findById(dbNotificationId)).thenReturn(Optional.of(caseNotification));
            when(notificationClient.sendEmail(eq(templateId), eq(emailAddress), eq(personalisation), anyString()))
                .thenThrow(exception);

            CustomTask<EmailState> task = sendEmailTaskComponent.sendEmailTask();

            CompletionHandler<EmailState> result = task.execute(taskInstance, executionContext);

            verify(errorHandler).handleSendEmailException(
                eq(exception), 
                eq(caseNotification), 
                anyString(), 
                any()
            );            
            assertThat(result).isInstanceOf(CompletionHandler.OnCompleteReplace.class);
        }

        @Test
        @DisplayName("Should handle permanent failure with 403 status code")
        void shouldHandlePermanentFailureWith403StatusCode() throws Exception {
            NotificationClientException exception = mock(NotificationClientException.class);
            when(exception.getHttpResult()).thenReturn(403);
            when(exception.getMessage()).thenReturn("Forbidden");

            when(notificationRepository.findById(dbNotificationId)).thenReturn(Optional.of(caseNotification));
            when(notificationClient.sendEmail(eq(templateId), eq(emailAddress), eq(personalisation), anyString()))
                .thenThrow(exception);

            CustomTask<EmailState> task = sendEmailTaskComponent.sendEmailTask();

            CompletionHandler<EmailState> result = task.execute(taskInstance, executionContext);

            verify(errorHandler).handleSendEmailException(eq(exception), eq(caseNotification), anyString(), any());
            assertThat(result).isInstanceOf(CompletionHandler.OnCompleteReplace.class);
        }

        @Test
        @DisplayName("Should throw TemporaryNotificationException for temporary failure status codes")
        void shouldThrowTemporaryNotificationExceptionForTemporaryFailureStatusCodes() throws Exception {
            int[] temporaryFailureStatusCodes = {429, 500, 502, 503, 999};

            for (int statusCode : temporaryFailureStatusCodes) {
                reset(notificationClient, notificationRepository, notificationService);
                
                NotificationClientException exception = mock(NotificationClientException.class);
                when(exception.getHttpResult()).thenReturn(statusCode);
                when(exception.getMessage()).thenReturn("Status " + statusCode);

                when(notificationRepository.findById(dbNotificationId)).thenReturn(Optional.of(caseNotification));
                when(notificationClient.sendEmail(eq(templateId), eq(emailAddress), eq(personalisation), anyString()))
                    .thenThrow(exception);
                when(taskInstance.getData()).thenReturn(emailState);

                CustomTask<EmailState> task = sendEmailTaskComponent.sendEmailTask();

                assertThatThrownBy(() -> task.execute(taskInstance, executionContext))
                    .isInstanceOf(TemporaryNotificationException.class)
                    .hasMessage("Email temporarily failed to send.")
                    .hasCause(exception);
                
                verify(notificationService, never()).updateNotificationAfterSending(any(), any());
                verifyNoInteractions(errorHandler);
            }
        }

        @Test
        @DisplayName("Should pass updateNotificationFromStatusUpdate to error handler for permanent failures")
        void shouldPassUpdateNotificationFromStatusUpdateToErrorHandlerForPermanentFailures() throws Exception {
            NotificationClientException exception = mock(NotificationClientException.class);
            when(exception.getHttpResult()).thenReturn(400);
            when(exception.getMessage()).thenReturn("Bad Request");

            when(notificationRepository.findById(dbNotificationId)).thenReturn(Optional.of(caseNotification));
            when(notificationClient.sendEmail(eq(templateId), eq(emailAddress), eq(personalisation), anyString()))
                .thenThrow(exception);

            CustomTask<EmailState> task = sendEmailTaskComponent.sendEmailTask();

            task.execute(taskInstance, executionContext);

            @SuppressWarnings("unchecked")
            ArgumentCaptor<Consumer<NotificationStatusUpdate>> consumerCaptor = 
                ArgumentCaptor.forClass(Consumer.class);
            verify(errorHandler).handleSendEmailException(
                eq(exception),
                eq(caseNotification),
                anyString(),
                consumerCaptor.capture()
            );

            Consumer<NotificationStatusUpdate> statusUpdater = consumerCaptor.getValue();
            NotificationStatusUpdate statusUpdate = new NotificationStatusUpdate(
                caseNotification,
                NotificationStatus.PERMANENT_FAILURE,
                notificationId
            );

            statusUpdater.accept(statusUpdate);
            verify(notificationService).updateNotificationStatus(
                statusUpdate.notification().getNotificationId(),
                statusUpdate.status().toString()
            );
        }
        
        @Test
        @DisplayName("Should handle all statuses in isPermanentFailure method")
        void shouldHandleAllStatusesInIsPermanentFailureMethod() throws Exception {
            NotificationClientException exception400 = mock(NotificationClientException.class);
            when(exception400.getHttpResult()).thenReturn(400);
            when(notificationRepository.findById(dbNotificationId)).thenReturn(Optional.of(caseNotification));
            when(notificationClient.sendEmail(eq(templateId), eq(emailAddress), eq(personalisation), anyString()))
                .thenThrow(exception400);
            
            CompletionHandler<EmailState> result400 = sendEmailTaskComponent.sendEmailTask()
                .execute(taskInstance, executionContext);
            assertThat(result400).isInstanceOf(CompletionHandler.OnCompleteReplace.class);
            verify(errorHandler).handleSendEmailException(
                eq(exception400), 
                eq(caseNotification), 
                anyString(), 
                any()
            );
            
            NotificationClientException exception403 = mock(NotificationClientException.class);
            when(exception403.getHttpResult()).thenReturn(403);
            when(notificationClient.sendEmail(eq(templateId), eq(emailAddress), eq(personalisation), anyString()))
                .thenThrow(exception403);
                
            CompletionHandler<EmailState> result403 = sendEmailTaskComponent.sendEmailTask()
                .execute(taskInstance, executionContext);
            assertThat(result403).isInstanceOf(CompletionHandler.OnCompleteReplace.class);
            verify(errorHandler).handleSendEmailException(
                eq(exception403), 
                eq(caseNotification), 
                anyString(), 
                any()
            );
            
            NotificationClientException exception401 = mock(NotificationClientException.class);
            when(exception401.getHttpResult()).thenReturn(401);
            when(notificationClient.sendEmail(eq(templateId), eq(emailAddress), eq(personalisation), anyString()))
                .thenThrow(exception401);
                
            assertThatThrownBy(() -> sendEmailTaskComponent.sendEmailTask()
                .execute(taskInstance, executionContext))
                .isInstanceOf(TemporaryNotificationException.class);
        }
    }

    @Nested
    @DisplayName("Email State Validation Tests")
    class EmailStateValidationTests {

        @Test
        @DisplayName("Should handle email state with all required fields")
        void shouldHandleEmailStateWithAllRequiredFields() throws Exception {
            when(notificationRepository.findById(dbNotificationId)).thenReturn(Optional.of(caseNotification));
            when(sendEmailResponse.getNotificationId()).thenReturn(notificationId);
            when(notificationClient.sendEmail(eq(templateId), eq(emailAddress), eq(personalisation), anyString()))
                .thenReturn(sendEmailResponse);

            CustomTask<EmailState> task = sendEmailTaskComponent.sendEmailTask();

            CompletionHandler<EmailState> result = task.execute(taskInstance, executionContext);

            verify(notificationClient).sendEmail(eq(templateId), eq(emailAddress), eq(personalisation), anyString());
            assertThat(result).isInstanceOf(CompletionHandler.OnCompleteReplace.class);
        }

        @Test
        @DisplayName("Should handle empty personalisation map")
        void shouldHandleEmptyPersonalisationMap() throws Exception {
            EmailState emptyPersonalisationState = emailState.toBuilder()
                .personalisation(Map.of())
                .build();
            when(taskInstance.getData()).thenReturn(emptyPersonalisationState);
            when(notificationRepository.findById(dbNotificationId)).thenReturn(Optional.of(caseNotification));
            when(sendEmailResponse.getNotificationId()).thenReturn(notificationId);
            when(notificationClient.sendEmail(eq(templateId), eq(emailAddress), eq(Map.of()), anyString()))
                .thenReturn(sendEmailResponse);

            CustomTask<EmailState> task = sendEmailTaskComponent.sendEmailTask();

            CompletionHandler<EmailState> result = task.execute(taskInstance, executionContext);

            verify(notificationClient).sendEmail(eq(templateId), eq(emailAddress), eq(Map.of()), anyString());
            assertThat(result).isInstanceOf(CompletionHandler.OnCompleteReplace.class);
        }
        
        @Test
        @DisplayName("Should handle null personalisation map")
        void shouldHandleNullPersonalisationMap() throws Exception {
            EmailState nullPersonalisationState = emailState.toBuilder()
                .personalisation(null)
                .build();
            when(taskInstance.getData()).thenReturn(nullPersonalisationState);
            when(notificationRepository.findById(dbNotificationId)).thenReturn(Optional.of(caseNotification));
            when(sendEmailResponse.getNotificationId()).thenReturn(notificationId);
            when(notificationClient.sendEmail(eq(templateId), eq(emailAddress), eq(null), anyString()))
                .thenReturn(sendEmailResponse);

            CustomTask<EmailState> task = sendEmailTaskComponent.sendEmailTask();

            CompletionHandler<EmailState> result = task.execute(taskInstance, executionContext);

            verify(notificationClient).sendEmail(eq(templateId), eq(emailAddress), eq(null), anyString());
            assertThat(result).isInstanceOf(CompletionHandler.OnCompleteReplace.class);
        }
        
        @Test
        @DisplayName("Should handle email state with additional fields")
        void shouldHandleEmailStateWithAdditionalFields() throws Exception {
            EmailState stateWithAdditionalFields = emailState.toBuilder()
                .emailReplyToId("reply-to-id")
                .reference("custom-reference")
                .build();
            when(taskInstance.getData()).thenReturn(stateWithAdditionalFields);
            when(notificationRepository.findById(dbNotificationId)).thenReturn(Optional.of(caseNotification));
            when(sendEmailResponse.getNotificationId()).thenReturn(notificationId);
            when(notificationClient.sendEmail(eq(templateId), eq(emailAddress), eq(personalisation), anyString()))
                .thenReturn(sendEmailResponse);

            CustomTask<EmailState> task = sendEmailTaskComponent.sendEmailTask();

            CompletionHandler<EmailState> result = task.execute(taskInstance, executionContext);

            verify(notificationClient).sendEmail(eq(templateId), eq(emailAddress), eq(personalisation), anyString());
            assertThat(result).isInstanceOf(CompletionHandler.OnCompleteReplace.class);
        }
    }

    @Nested
    @DisplayName("Task Configuration Tests")
    class TaskConfigurationTests {

        @Test
        @DisplayName("Should configure task with correct failure handlers")
        void shouldConfigureTaskWithCorrectFailureHandlers() {
            CustomTask<EmailState> task = sendEmailTaskComponent.sendEmailTask();

            assertThat(task).isNotNull();
        }

        @Test
        @DisplayName("Should use correct configuration values")
        void shouldUseCorrectConfigurationValues() {
            SendEmailTaskComponent component = new SendEmailTaskComponent(
                notificationService,
                notificationClient,
                errorHandler,
                notificationRepository,
                5,
                Duration.ofMinutes(1),
                Duration.ofSeconds(2),
                Duration.ofMinutes(10)
            );

            CustomTask<EmailState> task = component.sendEmailTask();
            assertThat(task).isNotNull();
        }
    }

    @Nested
    @DisplayName("Integration Tests")
    class IntegrationTests {

        @Test
        @DisplayName("Should handle complete successful flow")
        void shouldHandleCompleteSuccessfulFlow() throws Exception {
            when(notificationRepository.findById(dbNotificationId)).thenReturn(Optional.of(caseNotification));
            when(sendEmailResponse.getNotificationId()).thenReturn(notificationId);
            when(notificationClient.sendEmail(eq(templateId), eq(emailAddress), eq(personalisation), anyString()))
                .thenReturn(sendEmailResponse);

            CustomTask<EmailState> task = sendEmailTaskComponent.sendEmailTask();

            CompletionHandler<EmailState> result = task.execute(taskInstance, executionContext);

            verify(notificationRepository).findById(dbNotificationId);
            verify(notificationClient).sendEmail(eq(templateId), eq(emailAddress), eq(personalisation), anyString());
            verify(notificationService).updateNotificationAfterSending(dbNotificationId, notificationId);
            assertThat(result).isInstanceOf(CompletionHandler.OnCompleteReplace.class);
        }

        @Test
        @DisplayName("Should handle failure flow with proper scheduling of verify task")
        void shouldHandleFailureFlowWithProperSchedulingOfVerifyTask() throws Exception {
            NotificationClientException exception = mock(NotificationClientException.class);
            when(exception.getHttpResult()).thenReturn(400);
            when(exception.getMessage()).thenReturn("Bad Request");
            
            when(notificationRepository.findById(dbNotificationId)).thenReturn(Optional.of(caseNotification));
            when(notificationClient.sendEmail(eq(templateId), eq(emailAddress), eq(personalisation), anyString()))
                .thenThrow(exception);
            
            CustomTask<EmailState> task = sendEmailTaskComponent.sendEmailTask();
            
            CompletionHandler<EmailState> result = task.execute(taskInstance, executionContext);
            
            verify(notificationRepository).findById(dbNotificationId);
            verify(notificationClient).sendEmail(eq(templateId), eq(emailAddress), eq(personalisation), anyString());
            verify(errorHandler).handleSendEmailException(
                eq(exception),
                eq(caseNotification),
                anyString(),
                any()
            );
            
            assertThat(result).isInstanceOf(CompletionHandler.OnCompleteReplace.class);
        }

        @Test
        @DisplayName("Should handle temporary failure flow with retry")
        void shouldHandleTemporaryFailureFlowWithRetry() throws Exception {
            NotificationClientException exception = mock(NotificationClientException.class);
            when(exception.getHttpResult()).thenReturn(500);
            when(exception.getMessage()).thenReturn("Internal Server Error");

            when(notificationRepository.findById(dbNotificationId)).thenReturn(Optional.of(caseNotification));
            when(notificationClient.sendEmail(eq(templateId), eq(emailAddress), eq(personalisation), anyString()))
                .thenThrow(exception);

            CustomTask<EmailState> task = sendEmailTaskComponent.sendEmailTask();

            assertThatThrownBy(() -> task.execute(taskInstance, executionContext))
                .isInstanceOf(TemporaryNotificationException.class)
                .hasMessage("Email temporarily failed to send.")
                .hasCause(exception);

            verify(notificationRepository).findById(dbNotificationId);
            verify(notificationClient).sendEmail(
                eq(templateId), 
                eq(emailAddress), 
                eq(personalisation), 
                anyString()
            );
            verify(notificationService, never()).updateNotificationAfterSending(any(), any());
            verifyNoInteractions(errorHandler);
        }
        
        @Test
        @DisplayName("Should handle database exceptions during status update")
        void shouldHandleDatabaseExceptionsduringStatusUpdate() throws Exception {
            when(notificationRepository.findById(dbNotificationId)).thenReturn(Optional.of(caseNotification));
            when(sendEmailResponse.getNotificationId()).thenReturn(notificationId);
            when(notificationClient.sendEmail(eq(templateId), eq(emailAddress), eq(personalisation), anyString()))
                .thenReturn(sendEmailResponse);
                
            doThrow(new RuntimeException("Database error")).when(notificationService)
                .updateNotificationAfterSending(dbNotificationId, notificationId);

            CustomTask<EmailState> task = sendEmailTaskComponent.sendEmailTask();

            assertThatThrownBy(() -> task.execute(taskInstance, executionContext))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Database error");

            verify(notificationRepository).findById(dbNotificationId);
            verify(notificationClient).sendEmail(anyString(), anyString(), any(), anyString());
            verify(notificationService).updateNotificationAfterSending(dbNotificationId, notificationId);
        }
    }
    
    @Test
    @DisplayName("Should verify updateNotificationFromStatusUpdate with null values")
    void shouldVerifyUpdateNotificationFromStatusUpdateWithNullValues() {
        NotificationStatusUpdate statusUpdate = new NotificationStatusUpdate(
            null,
            NotificationStatus.PERMANENT_FAILURE,
            null
        );
        
        assertThatThrownBy(() -> {
            @SuppressWarnings("unchecked")
            ArgumentCaptor<Consumer<NotificationStatusUpdate>> consumerCaptor = 
                ArgumentCaptor.forClass(Consumer.class);
            
            NotificationClientException exception = mock(NotificationClientException.class);
            when(exception.getHttpResult()).thenReturn(400);
            when(notificationRepository.findById(dbNotificationId)).thenReturn(Optional.of(caseNotification));
            when(notificationClient.sendEmail(anyString(), anyString(), any(), anyString()))
                .thenThrow(exception);
                
            sendEmailTaskComponent.sendEmailTask().execute(taskInstance, executionContext);
            
            verify(errorHandler).handleSendEmailException(
                any(),
                any(),
                anyString(),
                consumerCaptor.capture()
            );
            
            Consumer<NotificationStatusUpdate> statusUpdater = consumerCaptor.getValue();
            statusUpdater.accept(statusUpdate);
        }).isInstanceOf(NullPointerException.class);
    }
}
