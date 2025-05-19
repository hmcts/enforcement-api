package uk.gov.hmcts.reform.enforcement.notify.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.hmcts.reform.enforcement.notify.entities.CaseNotification;
import uk.gov.hmcts.reform.enforcement.notify.exception.NotificationException;
import uk.gov.hmcts.reform.enforcement.notify.model.EmailNotificationRequest;
import uk.gov.hmcts.reform.enforcement.notify.model.NotificationStatus;
import uk.gov.hmcts.reform.enforcement.notify.model.NotificationType;
import uk.gov.hmcts.reform.enforcement.notify.repository.NotificationRepository;
import uk.gov.service.notify.NotificationClient;
import uk.gov.service.notify.NotificationClientException;
import uk.gov.service.notify.SendEmailResponse;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    private static final long STATUS_CHECK_DELAY = 1000L;
    private static final String SERVICE_AUTHORIZATION_HEADER = "ServiceAuthorization";
    private NotificationService notificationService;

    @Mock
    private NotificationClient notificationClient;

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private SendEmailResponse sendEmailResponse;

    @BeforeEach
    public void setUp() {
        notificationService = new NotificationService(notificationClient, notificationRepository, STATUS_CHECK_DELAY);
    }

    @Test
    @DisplayName("Should send email notification successfully")
    void shouldSendEmailNotificationSuccessfully() throws NotificationClientException {
        final EmailNotificationRequest emailNotificationRequest = createEmailNotificationRequest();
        final CaseNotification caseNotification = new CaseNotification();
        caseNotification.setNotificationId(UUID.randomUUID());
        
        UUID notificationId = UUID.randomUUID();
        
        when(notificationRepository.save(any(CaseNotification.class))).thenReturn(caseNotification);
        when(notificationClient.sendEmail(anyString(), anyString(), any(), anyString()))
            .thenReturn(sendEmailResponse);
        when(sendEmailResponse.getNotificationId()).thenReturn(notificationId);

        SendEmailResponse actualResponse = notificationService.sendEmail(emailNotificationRequest);

        assertThat(actualResponse).isEqualTo(sendEmailResponse);
        
        // Verify save is called twice
        verify(notificationRepository, times(2)).save(any(CaseNotification.class));
        
        // Capture the saved notifications
        ArgumentCaptor<CaseNotification> notificationCaptor = ArgumentCaptor.forClass(CaseNotification.class);
        verify(notificationRepository, times(2)).save(notificationCaptor.capture());
        
        // Get the list of captured values
        List<CaseNotification> capturedNotifications = notificationCaptor.getAllValues();
        // The second saved notification should have the provider notification ID
        CaseNotification lastSavedNotification = capturedNotifications.get(1);
        assertThat(lastSavedNotification.getProviderNotificationId()).isEqualTo(notificationId);
    }

    @Test
    @DisplayName("Should throw notification exception when email sending fails")
    void shouldThrowNotificationExceptionWhenEmailSendingFails() throws NotificationClientException {
        final EmailNotificationRequest emailNotificationRequest = createEmailNotificationRequest();
        final CaseNotification caseNotification = new CaseNotification();
        caseNotification.setNotificationId(UUID.randomUUID());
        
        when(notificationRepository.save(any(CaseNotification.class))).thenReturn(caseNotification);
        when(notificationClient.sendEmail(anyString(), anyString(), any(), anyString()))
            .thenThrow(new NotificationClientException("Failed to send email"));

        assertThatThrownBy(() -> notificationService.sendEmail(emailNotificationRequest))
            .isInstanceOf(NotificationException.class)
            .hasMessageContaining("Email failed to send, please try again");
        
        // Verify save is called twice
        verify(notificationRepository, times(2)).save(any(CaseNotification.class));
        
        // Capture the saved notifications
        ArgumentCaptor<CaseNotification> notificationCaptor = ArgumentCaptor.forClass(CaseNotification.class);
        verify(notificationRepository, times(2)).save(notificationCaptor.capture());
        
        // Get the list of captured values
        List<CaseNotification> capturedNotifications = notificationCaptor.getAllValues();
        // The second saved notification should have the TECHNICAL_FAILURE status
        CaseNotification lastSavedNotification = capturedNotifications.get(1);
        assertThat(lastSavedNotification.getStatus()).isEqualTo(NotificationStatus.TECHNICAL_FAILURE);
    }

    @Test
    @DisplayName("Should create case notification properly")
    void shouldCreateCaseNotificationProperly() {
        String recipient = "test@example.com";
        NotificationType type = NotificationType.EMAIL;
        UUID caseId = UUID.randomUUID();
        
        CaseNotification caseNotification = new CaseNotification();
        caseNotification.setNotificationId(UUID.randomUUID());
        
        when(notificationRepository.save(any(CaseNotification.class))).thenReturn(caseNotification);
        
        CaseNotification result = notificationService.createCaseNotification(recipient, type, caseId);
        
        assertThat(result).isEqualTo(caseNotification);
        
        ArgumentCaptor<CaseNotification> notificationCaptor = ArgumentCaptor.forClass(CaseNotification.class);
        verify(notificationRepository).save(notificationCaptor.capture());
        
        CaseNotification savedNotification = notificationCaptor.getValue();
        assertThat(savedNotification.getRecipient()).isEqualTo(recipient);
        assertThat(savedNotification.getType()).isEqualTo(type);
        assertThat(savedNotification.getCaseId()).isEqualTo(caseId);
        assertThat(savedNotification.getStatus()).isEqualTo(NotificationStatus.PENDING_SCHEDULE);
    }

    private EmailNotificationRequest createEmailNotificationRequest() {
        EmailNotificationRequest emailNotificationRequest = new EmailNotificationRequest();
        emailNotificationRequest.setEmailAddress("test@example.com");
        emailNotificationRequest.setTemplateId("template-id");
        Map<String, Object> personalisation = new HashMap<>();
        personalisation.put("name", "Test User");
        emailNotificationRequest.setPersonalisation(personalisation);
        return emailNotificationRequest;
    }
}
