package uk.gov.hmcts.reform.enforcement.notify.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessException;
import uk.gov.hmcts.reform.enforcement.notify.entities.CaseNotification;
import uk.gov.hmcts.reform.enforcement.notify.exception.NotificationException;
import uk.gov.hmcts.reform.enforcement.notify.model.EmailNotificationRequest;
import uk.gov.hmcts.reform.enforcement.notify.model.NotificationStatus;
import uk.gov.hmcts.reform.enforcement.notify.model.NotificationType;
import uk.gov.hmcts.reform.enforcement.notify.repository.NotificationRepository;
import uk.gov.service.notify.NotificationClient;
import uk.gov.service.notify.NotificationClientException;
import uk.gov.service.notify.Notification;
import uk.gov.service.notify.SendEmailResponse;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private NotificationClient notificationClient;
    
    @Mock
    private NotificationRepository notificationRepository;
    
    private NotificationService notificationService;
    
    private EmailNotificationRequest emailRequest;
    private CaseNotification mockCaseNotification;
    private SendEmailResponse mockSendEmailResponse;
    private UUID notificationId;
    private long statusCheckDelay = 100;
    
    @BeforeEach
    void setUp() {
        notificationId = UUID.randomUUID();
        notificationService = new NotificationService(notificationClient, notificationRepository, statusCheckDelay);
        
        Map<String, Object> personalisation = new HashMap<>();
        personalisation.put("name", "Jonathan");
        
        emailRequest = new EmailNotificationRequest();
        emailRequest.setTemplateId("template-123");
        emailRequest.setEmailAddress("test@example.com");
        emailRequest.setPersonalisation(personalisation);
        
        mockCaseNotification = new CaseNotification();
        mockCaseNotification.setNotificationId(UUID.randomUUID());
        
        mockSendEmailResponse = mock(SendEmailResponse.class);
    }
    
    @Test
    void testCreateCaseNotification() {
        String recipient = "test@example.com";
        NotificationType type = NotificationType.EMAIL;
        UUID caseId = UUID.randomUUID();
        
        when(notificationRepository.save(any(CaseNotification.class))).thenAnswer(invocation -> {
            CaseNotification saved = invocation.getArgument(0);
            saved.setNotificationId(UUID.randomUUID());
            return saved;
        });
        
        CaseNotification result = notificationService.createCaseNotification(recipient, type, caseId);
        
        assertNotNull(result);
        assertThat(result.getCaseId()).isEqualTo(caseId);
        assertThat(result.getType()).isEqualTo(type);
        assertThat(result.getRecipient()).isEqualTo(recipient);
        assertThat(result.getStatus()).isEqualTo(NotificationStatus.PENDING_SCHEDULE);
        verify(notificationRepository).save(any(CaseNotification.class));
    }
    
    @Test
    void testCreateCaseNotificationDatabaseError() {
        String recipient = "test@example.com";
        NotificationType type = NotificationType.EMAIL;
        UUID caseId = UUID.randomUUID();
        
        doThrow(new DataAccessException("DB error") {}).when(notificationRepository).save(any(CaseNotification.class));
        
        NotificationException exception = assertThrows(NotificationException.class, () -> 
            notificationService.createCaseNotification(recipient, type, caseId));
        
        assertThat(exception.getMessage()).isEqualTo("Failed to save Case Notification.");
    }
    
    @Test
    void testSendEmail() throws NotificationClientException {
        when(mockSendEmailResponse.getNotificationId()).thenReturn(notificationId);
        when(notificationRepository.save(any(CaseNotification.class))).thenReturn(mockCaseNotification);
        when(notificationClient.sendEmail(
            anyString(), anyString(), anyMap(), anyString()
        )).thenReturn(mockSendEmailResponse);
        
        SendEmailResponse result = notificationService.sendEmail(emailRequest);
        
        assertNotNull(result);
        assertSame(mockSendEmailResponse, result);
        verify(notificationClient).sendEmail(
            eq(emailRequest.getTemplateId()),
            eq(emailRequest.getEmailAddress()),
            eq(emailRequest.getPersonalisation()),
            anyString()
        );
        
        ArgumentCaptor<CaseNotification> notificationCaptor = ArgumentCaptor.forClass(CaseNotification.class);
        verify(notificationRepository, times(2)).save(notificationCaptor.capture());
        
        CaseNotification firstSave = notificationCaptor.getAllValues().get(0);
        assertThat(firstSave.getType()).isEqualTo(NotificationType.EMAIL);
        assertThat(firstSave.getRecipient()).isEqualTo(emailRequest.getEmailAddress());
        
        CaseNotification secondSave = notificationCaptor.getAllValues().get(1);
        assertThat(secondSave.getStatus()).isEqualTo(NotificationStatus.SUBMITTED);
        assertThat(secondSave.getProviderNotificationId()).isEqualTo(notificationId);
    }
    
    @Test
    void testSendEmailFailure() throws NotificationClientException {
        when(notificationRepository.save(any(CaseNotification.class))).thenReturn(mockCaseNotification);
        when(notificationClient.sendEmail(
            anyString(), anyString(), anyMap(), anyString()
        )).thenThrow(new NotificationClientException("API error"));
        
        NotificationException exception = assertThrows(NotificationException.class, () -> 
            notificationService.sendEmail(emailRequest));
        
        assertThat(exception.getMessage()).isEqualTo("Email failed to send, please try again.");
        
        verify(notificationRepository, times(2)).save(any(CaseNotification.class));
    }
    
    @Test
    void testCheckNotificationStatus() throws NotificationClientException {
        Notification mockNotification = mock(Notification.class);
        when(mockNotification.getStatus()).thenReturn("delivered");
        
        when(notificationClient.getNotificationById(notificationId.toString()))
            .thenReturn(mockNotification);
            
        when(notificationRepository.findByProviderNotificationId(notificationId))
            .thenReturn(Optional.of(mockCaseNotification));
        
        CompletableFuture<Notification> future = notificationService.checkNotificationStatus(notificationId.toString());
        
        Notification result = future.join();
        
        assertNotNull(result);
        assertSame(mockNotification, result);
        verify(notificationClient).getNotificationById(notificationId.toString());
        verify(notificationRepository).findByProviderNotificationId(notificationId);
        
        verify(notificationRepository).save(any(CaseNotification.class));
    }
    
    @Test
    void testCheckNotificationStatusNotFound() throws NotificationClientException {
        Notification mockNotification = mock(Notification.class);
        when(notificationClient.getNotificationById(notificationId.toString()))
            .thenReturn(mockNotification);
    
        when(notificationRepository.findByProviderNotificationId(notificationId))
            .thenReturn(Optional.empty());
    
        CompletableFuture<Notification> future = notificationService.checkNotificationStatus(notificationId.toString());
    
        Notification result = future.join();
    
        assertNotNull(result);
        verify(notificationRepository).findByProviderNotificationId(notificationId);
        verify(notificationRepository, never()).save(any(CaseNotification.class));
    }
    
    @Test
    void testCheckNotificationStatusClientException() throws NotificationClientException {
        when(notificationClient.getNotificationById(anyString()))
            .thenThrow(new NotificationClientException("API error"));
        
        CompletableFuture<Notification> future = notificationService.checkNotificationStatus(notificationId.toString());
        
        assertThrows(CompletionException.class, future::join);
    }
    
    @Test
    void testUpdateNotificationWithValidStatus() throws NotificationClientException {
        Notification mockNotification = mock(Notification.class);
        when(mockNotification.getStatus()).thenReturn("delivered");
        
        when(notificationClient.getNotificationById(notificationId.toString()))
            .thenReturn(mockNotification);
            
        when(notificationRepository.findByProviderNotificationId(notificationId))
            .thenReturn(Optional.of(mockCaseNotification));
        when(notificationRepository.save(any(CaseNotification.class))).thenReturn(mockCaseNotification);
        
        CompletableFuture<Notification> future = notificationService.checkNotificationStatus(notificationId.toString());
        future.join();
        
        ArgumentCaptor<CaseNotification> captor = ArgumentCaptor.forClass(CaseNotification.class);
        verify(notificationRepository).save(captor.capture());
        
        CaseNotification savedNotification = captor.getValue();
        assertThat(savedNotification.getStatus()).isEqualTo(NotificationStatus.DELIVERED);
    }
    
    @Test
    void testUpdateNotificationWithInvalidStatus() throws NotificationClientException {
        Notification mockNotification = mock(Notification.class);
        when(mockNotification.getStatus()).thenReturn("invalid-status");
        
        when(notificationClient.getNotificationById(notificationId.toString()))
            .thenReturn(mockNotification);
            
        when(notificationRepository.findByProviderNotificationId(notificationId))
            .thenReturn(Optional.of(mockCaseNotification));
        
        CompletableFuture<Notification> future = notificationService.checkNotificationStatus(notificationId.toString());
        future.join();
        
        verify(notificationRepository, never()).save(any(CaseNotification.class));
    }
    
    @Test
    void testSendingStatusSetsSubmittedTime() {
        mockCaseNotification.setSubmittedAt(null);
        when(mockSendEmailResponse.getNotificationId()).thenReturn(notificationId);
        
        when(notificationRepository.save(any(CaseNotification.class))).thenAnswer(i -> {
            CaseNotification notification = i.getArgument(0);
            if (notification.getStatus() == NotificationStatus.SENDING) {
                assertNotNull(notification.getSubmittedAt());
            }
            return notification;
        });
        
        try {
            when(notificationClient.sendEmail(anyString(), anyString(), anyMap(), anyString()))
                .thenReturn(mockSendEmailResponse);
            notificationService.sendEmail(emailRequest);
        } catch (NotificationClientException e) {
            // No exception in this case
        }
        
        verify(notificationRepository, times(2)).save(any(CaseNotification.class));
    }
    
    @Test
    void testDatabaseErrorHandling() throws NotificationClientException {
        Notification mockNotification = mock(Notification.class);
        when(mockNotification.getStatus()).thenReturn("delivered");
        
        when(notificationClient.getNotificationById(notificationId.toString()))
            .thenReturn(mockNotification);
            
        when(notificationRepository.findByProviderNotificationId(notificationId))
            .thenReturn(Optional.of(mockCaseNotification));
        
        when(notificationRepository.save(any(CaseNotification.class)))
            .thenThrow(new RuntimeException("Database error"));
        
        CompletableFuture<Notification> future = notificationService.checkNotificationStatus(notificationId.toString());
        
        Notification result = future.join();
        assertNotNull(result);
    }
    
    @Test
    void testStatusTransitionFromSubmittedToDelivered() throws NotificationClientException {
        Notification mockNotification = mock(Notification.class);
        when(mockNotification.getStatus()).thenReturn("delivered");
        
        when(notificationClient.getNotificationById(notificationId.toString()))
            .thenReturn(mockNotification);
            
        mockCaseNotification.setStatus(NotificationStatus.SUBMITTED);
        when(notificationRepository.findByProviderNotificationId(notificationId))
            .thenReturn(Optional.of(mockCaseNotification));
        
        CompletableFuture<Notification> future = notificationService.checkNotificationStatus(notificationId.toString());
        future.join();
        
        ArgumentCaptor<CaseNotification> captor = ArgumentCaptor.forClass(CaseNotification.class);
        verify(notificationRepository).save(captor.capture());
        
        CaseNotification savedNotification = captor.getValue();
        assertThat(savedNotification.getStatus()).isEqualTo(NotificationStatus.DELIVERED);
    }
}
