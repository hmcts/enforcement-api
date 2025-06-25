package uk.gov.hmcts.reform.enforcement.notify.service;

import com.github.kagkarlsson.scheduler.SchedulerClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.reform.enforcement.notify.entities.CaseNotification;
import uk.gov.hmcts.reform.enforcement.notify.exception.NotificationException;
import uk.gov.hmcts.reform.enforcement.notify.model.EmailNotificationRequest;
import uk.gov.hmcts.reform.enforcement.notify.model.EmailNotificationResponse;
import uk.gov.hmcts.reform.enforcement.notify.model.EmailState;
import uk.gov.hmcts.reform.enforcement.notify.model.NotificationStatus;
import uk.gov.hmcts.reform.enforcement.notify.repository.NotificationRepository;
import uk.gov.hmcts.reform.enforcement.notify.task.SendEmailTaskComponent;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static java.util.UUID.randomUUID;
import static uk.gov.hmcts.reform.enforcement.notify.model.NotificationStatus.PENDING_SCHEDULE;
import static uk.gov.hmcts.reform.enforcement.notify.model.NotificationStatus.SCHEDULED;
import static uk.gov.hmcts.reform.enforcement.notify.model.NotificationType.EMAIL;

@Service
@Slf4j
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final SchedulerClient schedulerClient;

    public NotificationService(NotificationRepository notificationRepository,
                               SchedulerClient schedulerClient) {
        this.notificationRepository = notificationRepository;
        this.schedulerClient = schedulerClient;
    }

    public EmailNotificationResponse scheduleEmailNotification(EmailNotificationRequest emailRequest) {
        String taskId = randomUUID().toString();

        CaseNotification caseNotification = createCaseNotification(
            emailRequest.getEmailAddress(),
            randomUUID(),
            taskId
        );

        EmailState emailState = new EmailState(
            taskId,
            emailRequest.getEmailAddress(),
            emailRequest.getTemplateId(),
            emailRequest.getPersonalisation(),
            emailRequest.getReference(),
            emailRequest.getEmailReplyToId(),
            null,
            caseNotification.getNotificationId()
        );

        // Set initial status to SCHEDULED
        updateNotificationStatus(caseNotification, SCHEDULED, null);

        // Schedule a task that will update to SUBMITTED within 1-3 seconds per Acceptance Criteria
        boolean scheduled = schedulerClient.scheduleIfNotExists(
            SendEmailTaskComponent.sendEmailTask
                .instance(taskId)
                .data(emailState)
                .scheduledTo(Instant.now())
        );

        if (!scheduled) {
            log.warn("Task with ID {} already exists and has not been scheduled", taskId);
        }

        EmailNotificationResponse response = new EmailNotificationResponse();
        response.setTaskId(taskId);
        response.setStatus(SCHEDULED.toString());
        response.setNotificationId(caseNotification.getNotificationId());

        log.info("Email notification scheduled with task ID: {} and notification ID: {}",
                 taskId, caseNotification.getNotificationId());

        return response;
    }

    public void updateNotificationAfterSending(UUID dbNotificationId, UUID providerNotificationId) {
        Optional<CaseNotification> notificationOpt = notificationRepository.findById(dbNotificationId);
        if (notificationOpt.isEmpty()) {
            log.error("Notification not found with ID: {}", dbNotificationId);
            return;
        }

        CaseNotification notification = notificationOpt.get();
        updateNotificationStatus(notification, NotificationStatus.SUBMITTED, providerNotificationId);
    }

    public void updateNotificationAfterFailure(UUID dbNotificationId, Exception exception) {
        Optional<CaseNotification> notificationOpt = notificationRepository.findById(dbNotificationId);
        if (notificationOpt.isEmpty()) {
            log.error("Notification not found with ID on failure: {}", dbNotificationId);
            return;
        }

        CaseNotification notification = notificationOpt.get();
        // Set to SUBMITTED per acceptance criteria
        updateNotificationStatus(notification, NotificationStatus.SUBMITTED, null);
        log.error("Email sending failed for notification ID: {}, error: {}",
                  dbNotificationId, exception.getMessage());
    }

    private CaseNotification createCaseNotification(String recipient, UUID caseId, String taskId) {
        CaseNotification toSaveNotification = new CaseNotification();

        toSaveNotification.setCaseId(caseId);
        toSaveNotification.setStatus(PENDING_SCHEDULE);
        toSaveNotification.setType(EMAIL.toString());
        toSaveNotification.setRecipient(recipient);

        try {
            CaseNotification savedNotification = notificationRepository.save(toSaveNotification);
            log.info(
                "Case Notification with ID {} has been saved to the database with task ID {}",
                savedNotification.getNotificationId(), taskId
            );
            return savedNotification;
        } catch (DataAccessException dataAccessException) {
            log.error(
                "Failed to save Case Notification with Case ID: {}. Reason: {}",
                toSaveNotification.getCaseId(),
                dataAccessException.getMessage(),
                dataAccessException
            );
            throw new NotificationException("Failed to save Case Notification.", dataAccessException);
        }
    }

    public void updateNotificationStatus(UUID dbNotificationId, String statusString) {
        notificationRepository.findById(dbNotificationId)
            .ifPresentOrElse(
                notification -> processStatusUpdate(notification, statusString),
                () -> log.error("Notification not found with ID on status update: {}", dbNotificationId)
            );
    }

    private void updateNotificationStatus(
        CaseNotification notification,
        NotificationStatus status,
        UUID providerNotificationId) {

        try {
            notification.setStatus(status);
            notification.setLastUpdatedAt(Instant.now());

            if (providerNotificationId != null) {
                notification.setProviderNotificationId(providerNotificationId);
            }

            if (status == NotificationStatus.SENDING || status == NotificationStatus.SUBMITTED) {
                notification.setSubmittedAt(Instant.now());
            }

            notificationRepository.save(notification);
            log.info("Updated notification status to {} for notification ID: {}",
                     status, notification.getNotificationId());
        } catch (Exception e) {
            log.error("Error updating notification status to {}: {}",
                      status, e.getMessage(), e);
        }
    }

    private void processStatusUpdate(CaseNotification notification, String statusString) {
        try {
            NotificationStatus status = NotificationStatus.fromString(statusString);
            updateNotificationStatus(notification, status, null);
        } catch (IllegalArgumentException e) {
            log.warn("Unknown notification status: {}", statusString);
        }
    }
}
