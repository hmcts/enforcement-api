package uk.gov.hmcts.reform.enforcement.testingsupport.endpoint;

import com.github.kagkarlsson.scheduler.SchedulerClient;
import com.github.kagkarlsson.scheduler.task.Task;
import com.github.kagkarlsson.scheduler.task.TaskInstance;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import uk.gov.hmcts.reform.enforcement.notify.model.EmailNotificationRequest;
import uk.gov.hmcts.reform.enforcement.notify.service.NotificationService;
import uk.gov.service.notify.SendEmailResponse;

import java.net.URI;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TestingSupportControllerTest {

    @Mock
    private NotificationService notificationService;
    
    @Mock
    private SchedulerClient schedulerClient;
    
    @Mock
    private Task<Void> helloWorldTask;

    @InjectMocks
    private TestingSupportController underTest;

    @Test
    void testSendEmail_Success() {
        var notificationId = UUID.randomUUID();
        var templateId = UUID.randomUUID();
        var unsubscribeUrl = URI.create("https://unsubscribe.example.com");

        var emailRequest = new EmailNotificationRequest(
                "test@example.com",
                "templateId",
                null,
                "reference",
                "emailReplyToId"
        );

        SendEmailResponse mockResponse = mock(SendEmailResponse.class);
        when(mockResponse.getNotificationId()).thenReturn(notificationId);
        when(mockResponse.getReference()).thenReturn(Optional.of("reference"));
        when(mockResponse.getOneClickUnsubscribeURL()).thenReturn(Optional.of(unsubscribeUrl));
        when(mockResponse.getTemplateId()).thenReturn(templateId);
        when(mockResponse.getTemplateVersion()).thenReturn(1);
        when(mockResponse.getTemplateUri()).thenReturn("/template/uri");
        when(mockResponse.getBody()).thenReturn("Email body content");
        when(mockResponse.getSubject()).thenReturn("Email subject");
        when(mockResponse.getFromEmail()).thenReturn(Optional.of("noreply@example.com"));

        when(notificationService.sendEmail(any(EmailNotificationRequest.class))).thenReturn(mockResponse);

        var response = underTest.sendEmail(
                "Bearer token",
                "ServiceAuthToken",
                emailRequest
        );

        assertThat(response.getBody())
                .extracting(
                        SendEmailResponse::getNotificationId,
                        SendEmailResponse::getReference,
                        SendEmailResponse::getOneClickUnsubscribeURL,
                        SendEmailResponse::getTemplateId,
                        SendEmailResponse::getTemplateVersion,
                        SendEmailResponse::getTemplateUri,
                        SendEmailResponse::getBody,
                        SendEmailResponse::getSubject,
                        SendEmailResponse::getFromEmail
                )
                .containsExactly(
                        notificationId,
                        Optional.of("reference"),
                        Optional.of(unsubscribeUrl),
                        templateId,
                        1,
                        "/template/uri",
                        "Email body content",
                        "Email subject",
                        Optional.of("noreply@example.com"));

        verify(notificationService).sendEmail(emailRequest);
    }
    
    @SuppressWarnings("unchecked")
    @Test
    void shouldScheduleSampleJobSuccessfully_WhenGivenValidDelay() {
        var taskId = "test-task-id";
        var delaySeconds = 5;
        var expectedTime = Instant.now().plusSeconds(delaySeconds);
        
        TaskInstance<Void> mockTaskInstance = mock(TaskInstance.class);
        when(helloWorldTask.instance(anyString())).thenReturn(mockTaskInstance);

        var response = underTest.createSampleJob(
            delaySeconds,
            "Bearer token",
            "ServiceAuthToken"
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("Hello World task scheduled successfully");
        assertThat(response.getBody()).contains("execution time:");

        ArgumentCaptor<TaskInstance<Void>> taskInstanceCaptor = ArgumentCaptor.forClass(TaskInstance.class);
        ArgumentCaptor<Instant> executionTimeCaptor = ArgumentCaptor.forClass(Instant.class);

        verify(schedulerClient, times(1)).scheduleIfNotExists(
            taskInstanceCaptor.capture(), 
            executionTimeCaptor.capture()
        );
        
        var capturedInstance = taskInstanceCaptor.getValue();
        var capturedTime = executionTimeCaptor.getValue();
        
        assertThat(capturedInstance).isSameAs(mockTaskInstance);
        assertThat(capturedTime).isAfterOrEqualTo(expectedTime.minusSeconds(1));
        assertThat(capturedTime).isBeforeOrEqualTo(expectedTime.plusSeconds(1));
        
        verifyNoMoreInteractions(schedulerClient);
    }

    @SuppressWarnings("unchecked")
    @Test
    void shouldReturnErrorResponse_WhenSchedulingFails() {
        var errorMessage = "Scheduler database connection failure";
        TaskInstance<Void> mockTaskInstance = mock(TaskInstance.class);
        when(helloWorldTask.instance(anyString())).thenReturn(mockTaskInstance);

        doThrow(new RuntimeException(errorMessage))
            .when(schedulerClient)
            .scheduleIfNotExists(any(TaskInstance.class), any(Instant.class));

        var response = underTest.createSampleJob(
            2,
            "Bearer token",
            "ServiceAuthToken"
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).contains("An error occurred while scheduling the Hello World task.");
        
        verify(schedulerClient, times(1)).scheduleIfNotExists(any(TaskInstance.class), any(Instant.class));
        verifyNoMoreInteractions(schedulerClient);
    }
}
