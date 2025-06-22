package uk.gov.hmcts.reform.enforcement.testingsupport.endpoint;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import uk.gov.hmcts.reform.enforcement.notify.exception.NotificationException;
import uk.gov.hmcts.reform.enforcement.notify.model.EmailNotificationRequest;
import uk.gov.hmcts.reform.enforcement.notify.model.EmailNotificationResponse;
import uk.gov.hmcts.reform.enforcement.notify.model.NotificationStatus;
import uk.gov.hmcts.reform.enforcement.notify.service.NotificationService;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotifyControllerTest {

    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private NotifyController notifyController;

    private EmailNotificationRequest emailRequest;
    private EmailNotificationResponse emailResponse;
    private String authorization;
    private String serviceAuthorization;

    @BeforeEach
    void setUp() {
        authorization = "Bearer token123";
        serviceAuthorization = "ServiceAuth token456";

        Map<String, Object> personalisation = new HashMap<>();
        personalisation.put("name", "John Doe");
        personalisation.put("reference", "REF123");

        emailRequest = new EmailNotificationRequest();
        emailRequest.setEmailAddress("test@example.com");
        emailRequest.setTemplateId("template-123");
        emailRequest.setPersonalisation(personalisation);
        emailRequest.setReference("notification-ref");
        emailRequest.setEmailReplyToId("reply-to-123");

        emailResponse = new EmailNotificationResponse();
        emailResponse.setTaskId("task-123");
        emailResponse.setStatus(NotificationStatus.SCHEDULED.toString());
        emailResponse.setNotificationId(UUID.randomUUID());
    }

    @Test
    void sendEmail_ShouldReturnAccepted_WhenEmailScheduledSuccessfully() {
        when(notificationService.scheduleEmailNotification(emailRequest)).thenReturn(emailResponse);

        ResponseEntity<EmailNotificationResponse> response = notifyController.sendEmail(
            authorization, serviceAuthorization, emailRequest);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(response.getBody()).isEqualTo(emailResponse);
        assertThat(response.getBody().getTaskId()).isEqualTo("task-123");
        assertThat(response.getBody().getStatus()).isEqualTo(NotificationStatus.SCHEDULED.toString());

        verify(notificationService).scheduleEmailNotification(emailRequest);
    }

    @Test
    void sendEmail_ShouldReturnInternalServerError_WhenNotificationServiceThrowsException() {
        when(notificationService.scheduleEmailNotification(emailRequest))
            .thenThrow(new NotificationException("Failed to schedule notification", 
                                                  new RuntimeException("Database error")));

        ResponseEntity<EmailNotificationResponse> response = notifyController.sendEmail(
            authorization, serviceAuthorization, emailRequest);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isNull();

        verify(notificationService).scheduleEmailNotification(emailRequest);
    }

    @Test
    void sendEmail_ShouldReturnInternalServerError_WhenRuntimeExceptionOccurs() {
        when(notificationService.scheduleEmailNotification(emailRequest))
            .thenThrow(new RuntimeException("Unexpected error"));

        ResponseEntity<EmailNotificationResponse> response = notifyController.sendEmail(
            authorization, serviceAuthorization, emailRequest);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isNull();

        verify(notificationService).scheduleEmailNotification(emailRequest);
    }

    @Test
    void sendEmail_ShouldUseDefaultAuthorization_WhenHeaderNotProvided() {
        when(notificationService.scheduleEmailNotification(emailRequest)).thenReturn(emailResponse);

        ResponseEntity<EmailNotificationResponse> response = notifyController.sendEmail(
            "DummyId", serviceAuthorization, emailRequest);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(response.getBody()).isEqualTo(emailResponse);

        verify(notificationService).scheduleEmailNotification(emailRequest);
    }

    @Test
    void constructor_ShouldInitializeFields() {
        NotifyController controller = new NotifyController(notificationService);
        assertThat(controller).isNotNull();
    }
}
