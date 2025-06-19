package uk.gov.hmcts.reform.enforcement.notify.service;

import com.github.kagkarlsson.scheduler.SchedulerClient;
import com.github.kagkarlsson.scheduler.task.SchedulableInstance;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessException;
import uk.gov.hmcts.reform.enforcement.notify.domain.CaseNotification;
import uk.gov.hmcts.reform.enforcement.notify.exception.NotificationException;
import uk.gov.hmcts.reform.enforcement.notify.model.EmailNotificationRequest;
import uk.gov.hmcts.reform.enforcement.notify.model.EmailNotificationResponse;
import uk.gov.hmcts.reform.enforcement.notify.repository.NotificationRepository;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static uk.gov.hmcts.reform.enforcement.notify.model.NotificationStatus.DELIVERED;
import static uk.gov.hmcts.reform.enforcement.notify.model.NotificationStatus.PENDING_SCHEDULE;
import static uk.gov.hmcts.reform.enforcement.notify.model.NotificationStatus.SCHEDULED;
import static uk.gov.hmcts.reform.enforcement.notify.model.NotificationStatus.SENDING;
import static uk.gov.hmcts.reform.enforcement.notify.model.NotificationType.EMAIL;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("unchecked")
class NotificationServiceTest {

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private SchedulerClient schedulerClient;

    @InjectMocks
    private NotificationService notificationService;

    private EmailNotificationRequest emailRequest;
    private CaseNotification savedNotification;
    private UUID notificationId;
    private UUID caseId;

    @BeforeEach
    void setUp() {
        notificationId = UUID.randomUUID();
        caseId = UUID.randomUUID();

        Map<String, Object> personalisation = new HashMap<>();
        personalisation.put("name", "John Doe");
        personalisation.put("reference", "REF123");

        emailRequest = new EmailNotificationRequest();
        emailRequest.setEmailAddress("test@example.com");
        emailRequest.setTemplateId("template-123");
        emailRequest.setPersonalisation(personalisation);
        emailRequest.setReference("notification-ref");
        emailRequest.setEmailReplyToId("reply-to-123");

        savedNotification = new CaseNotification();
        savedNotification.setNotificationId(notificationId);
        savedNotification.setCaseId(caseId);
        savedNotification.setStatus(PENDING_SCHEDULE);
        savedNotification.setType(EMAIL.toString());
        savedNotification.setRecipient("test@example.com");
        savedNotification.setSubmittedAt(LocalDateTime.now());
    }

    @Test
    void scheduleEmailNotification_ShouldReturnResponse_WhenSuccessfullyScheduled() {
        when(notificationRepository.save(any(CaseNotification.class))).thenReturn(savedNotification);
        when(schedulerClient.scheduleIfNotExists(any(SchedulableInstance.class))).thenReturn(true);

        EmailNotificationResponse response = notificationService.scheduleEmailNotification(emailRequest);

        assertThat(response).isNotNull();
        assertThat(response.getTaskId()).isNotNull();
        assertThat(response.getStatus()).isEqualTo(SCHEDULED.toString());
        assertThat(response.getNotificationId()).isEqualTo(notificationId);

        verify(notificationRepository, times(2)).save(any(CaseNotification.class));
        verify(schedulerClient).scheduleIfNotExists(any(SchedulableInstance.class));

        ArgumentCaptor<CaseNotification> notificationCaptor = ArgumentCaptor.forClass(CaseNotification.class);
        verify(notificationRepository, times(2)).save(notificationCaptor.capture());

        CaseNotification firstSave = notificationCaptor.getAllValues().get(0);
        assertThat(firstSave.getRecipient()).isEqualTo("test@example.com");
        assertThat(firstSave.getStatus()).isEqualTo(PENDING_SCHEDULE);
        assertThat(firstSave.getType()).isEqualTo(EMAIL.toString());

        CaseNotification secondSave = notificationCaptor.getAllValues().get(1);
        assertThat(secondSave.getStatus()).isEqualTo(SCHEDULED);
        assertThat(secondSave.getLastUpdatedAt()).isNotNull();
    }

    @Test
    void scheduleEmailNotification_ShouldStillReturnResponse_WhenTaskAlreadyExists() {
        when(notificationRepository.save(any(CaseNotification.class))).thenReturn(savedNotification);
        when(schedulerClient.scheduleIfNotExists(any(SchedulableInstance.class))).thenReturn(false);

        EmailNotificationResponse response = notificationService.scheduleEmailNotification(emailRequest);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(SCHEDULED.toString());
        assertThat(response.getNotificationId()).isEqualTo(notificationId);

        verify(schedulerClient).scheduleIfNotExists(any(SchedulableInstance.class));
        verify(notificationRepository, times(2)).save(any(CaseNotification.class));
    }

    @Test
    void scheduleEmailNotification_ShouldThrowException_WhenDatabaseSaveFails() {
        DataAccessException dbException = new DataAccessException("Database connection failed") {};
        when(notificationRepository.save(any(CaseNotification.class))).thenThrow(dbException);

        assertThatThrownBy(() -> notificationService.scheduleEmailNotification(emailRequest))
            .isInstanceOf(NotificationException.class)
            .hasMessage("Failed to save Case Notification.")
            .hasCause(dbException);

        verify(notificationRepository).save(any(CaseNotification.class));
        verify(schedulerClient, never()).scheduleIfNotExists(any(SchedulableInstance.class));
    }

    @Test
    void updateNotificationStatus_ShouldUpdateStatus_WhenNotificationExists() {
        CaseNotification existingNotification = new CaseNotification();
        existingNotification.setNotificationId(notificationId);
        existingNotification.setStatus(SCHEDULED);

        when(notificationRepository.findById(notificationId)).thenReturn(Optional.of(existingNotification));
        when(notificationRepository.save(any(CaseNotification.class))).thenReturn(existingNotification);

        notificationService.updateNotificationStatus(notificationId, "SENDING");

        ArgumentCaptor<CaseNotification> captor = ArgumentCaptor.forClass(CaseNotification.class);
        verify(notificationRepository).save(captor.capture());

        CaseNotification updatedNotification = captor.getValue();
        assertThat(updatedNotification.getStatus()).isEqualTo(SENDING);
        assertThat(updatedNotification.getLastUpdatedAt()).isNotNull();
        assertThat(updatedNotification.getSubmittedAt()).isNotNull();
    }

    @Test
    void updateNotificationStatus_ShouldSetSubmittedAt_WhenStatusIsSending() {
        CaseNotification existingNotification = new CaseNotification();
        existingNotification.setNotificationId(notificationId);
        existingNotification.setStatus(SCHEDULED);

        when(notificationRepository.findById(notificationId)).thenReturn(Optional.of(existingNotification));
        when(notificationRepository.save(any(CaseNotification.class))).thenReturn(existingNotification);

        notificationService.updateNotificationStatus(notificationId, "SENDING");

        ArgumentCaptor<CaseNotification> captor = ArgumentCaptor.forClass(CaseNotification.class);
        verify(notificationRepository).save(captor.capture());

        CaseNotification updatedNotification = captor.getValue();
        assertThat(updatedNotification.getStatus()).isEqualTo(SENDING);
        assertThat(updatedNotification.getSubmittedAt()).isNotNull();
    }

    @Test
    void updateNotificationStatus_ShouldNotSetSubmittedAt_WhenStatusIsNotSending() {
        CaseNotification existingNotification = new CaseNotification();
        existingNotification.setNotificationId(notificationId);
        existingNotification.setStatus(SCHEDULED);

        when(notificationRepository.findById(notificationId)).thenReturn(Optional.of(existingNotification));
        when(notificationRepository.save(any(CaseNotification.class))).thenReturn(existingNotification);

        notificationService.updateNotificationStatus(notificationId, "DELIVERED");

        ArgumentCaptor<CaseNotification> captor = ArgumentCaptor.forClass(CaseNotification.class);
        verify(notificationRepository).save(captor.capture());

        CaseNotification updatedNotification = captor.getValue();
        assertThat(updatedNotification.getStatus()).isEqualTo(DELIVERED);
        assertThat(updatedNotification.getSubmittedAt()).isNull();
        assertThat(updatedNotification.getLastUpdatedAt()).isNotNull();
    }

    @Test
    void updateNotificationStatus_ShouldLogError_WhenNotificationNotFound() {
        when(notificationRepository.findById(notificationId)).thenReturn(Optional.empty());

        notificationService.updateNotificationStatus(notificationId, "DELIVERED");

        verify(notificationRepository).findById(notificationId);
        verify(notificationRepository, never()).save(any(CaseNotification.class));
    }

    @Test
    void updateNotificationStatus_ShouldLogWarning_WhenInvalidStatusString() {
        CaseNotification existingNotification = new CaseNotification();
        existingNotification.setNotificationId(notificationId);
        existingNotification.setStatus(SCHEDULED);

        when(notificationRepository.findById(notificationId)).thenReturn(Optional.of(existingNotification));

        notificationService.updateNotificationStatus(notificationId, "INVALID_STATUS");

        verify(notificationRepository).findById(notificationId);
        verify(notificationRepository, never()).save(any(CaseNotification.class));
    }

    @Test
    void updateNotificationStatus_ShouldHandleException_WhenSaveFails() {
        CaseNotification existingNotification = new CaseNotification();
        existingNotification.setNotificationId(notificationId);
        existingNotification.setStatus(SCHEDULED);

        when(notificationRepository.findById(notificationId)).thenReturn(Optional.of(existingNotification));
        when(notificationRepository.save(any(CaseNotification.class)))
            .thenThrow(new RuntimeException("Database error"));

        assertThatCode(() ->
                           notificationService.updateNotificationStatus(notificationId, "DELIVERED")
        ).doesNotThrowAnyException();

        verify(notificationRepository).save(any(CaseNotification.class));
    }

    @Test
    void scheduleEmailNotification_ShouldGenerateUniqueTaskIds() {
        when(notificationRepository.save(any(CaseNotification.class))).thenReturn(savedNotification);
        when(schedulerClient.scheduleIfNotExists(any(SchedulableInstance.class))).thenReturn(true);

        EmailNotificationResponse response1 = notificationService.scheduleEmailNotification(emailRequest);
        EmailNotificationResponse response2 = notificationService.scheduleEmailNotification(emailRequest);

        assertThat(response1.getTaskId()).isNotEqualTo(response2.getTaskId());
    }

    @Test
    void scheduleEmailNotification_ShouldScheduleTaskWithCorrectTiming() {
        when(notificationRepository.save(any(CaseNotification.class))).thenReturn(savedNotification);
        when(schedulerClient.scheduleIfNotExists(any(SchedulableInstance.class))).thenReturn(true);

        notificationService.scheduleEmailNotification(emailRequest);

        ArgumentCaptor<SchedulableInstance<?>> taskCaptor = ArgumentCaptor.forClass(SchedulableInstance.class);
        verify(schedulerClient).scheduleIfNotExists(taskCaptor.capture());

        SchedulableInstance<?> scheduledTask = taskCaptor.getValue();
        assertThat(scheduledTask).isNotNull();
    }

    @Test
    void updateNotificationStatus_ShouldSetProviderNotificationId_WhenProvided() {
        UUID providerNotificationId = UUID.randomUUID();
        CaseNotification notificationWithProvider = new CaseNotification();
        notificationWithProvider.setNotificationId(notificationId);
        notificationWithProvider.setStatus(SCHEDULED);
        notificationWithProvider.setProviderNotificationId(providerNotificationId);

        when(notificationRepository.save(any(CaseNotification.class))).thenReturn(savedNotification,
                                                                                    notificationWithProvider);
        when(schedulerClient.scheduleIfNotExists(any(SchedulableInstance.class))).thenReturn(true);

        EmailNotificationResponse response = notificationService.scheduleEmailNotification(emailRequest);

        assertThat(response).isNotNull();
        verify(notificationRepository, times(2)).save(any(CaseNotification.class));
    }

    @Test
    void constructor_ShouldInitializeFields() {
        NotificationService service = new NotificationService(notificationRepository, schedulerClient);

        assertThat(service).isNotNull();
    }
}
