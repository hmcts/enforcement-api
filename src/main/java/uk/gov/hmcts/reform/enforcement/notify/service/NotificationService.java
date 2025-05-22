package uk.gov.hmcts.reform.enforcement.notify.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.scheduling.annotation.Async;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.reform.enforcement.notify.entities.CaseNotification;
import uk.gov.hmcts.reform.enforcement.notify.exception.NotificationException;
import uk.gov.hmcts.reform.enforcement.notify.model.EmailNotificationRequest;
import uk.gov.hmcts.reform.enforcement.notify.model.NotificationStatus;
import uk.gov.hmcts.reform.enforcement.notify.model.NotificationType;
import uk.gov.hmcts.reform.enforcement.notify.repository.NotificationRepository;
import uk.gov.service.notify.NotificationClient;
import uk.gov.service.notify.NotificationClientException;
import uk.gov.service.notify.SendEmailResponse;
import uk.gov.service.notify.Notification;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
public class NotificationService {
    private final NotificationClient notificationClient;
    private final NotificationRepository notificationRepository;
    private final long statusCheckDelay;

    public NotificationService(
        NotificationClient notificationClient,
        NotificationRepository notificationRepository,
        @Value("${notify.status-check-delay-millis:3000}") long statusCheckDelay) {
        this.notificationClient = notificationClient;
        this.notificationRepository = notificationRepository;
        this.statusCheckDelay = statusCheckDelay;
    }

    public SendEmailResponse sendEmail(EmailNotificationRequest emailRequest) {
        SendEmailResponse sendEmailResponse;
        final String destinationAddress = emailRequest.getEmailAddress();
        final String templateId = emailRequest.getTemplateId();
        final Map<String, Object> personalisation = emailRequest.getPersonalisation();
        final String referenceId = UUID.randomUUID().toString();

        CaseNotification caseNotification = createCaseNotification(
            emailRequest.getEmailAddress(), 
            NotificationType.EMAIL, 
            UUID.randomUUID()
        );

        try {
            sendEmailResponse = notificationClient.sendEmail(
                templateId,
                destinationAddress,
                personalisation,
                referenceId
            );

            log.debug("Email sent successfully. Reference ID: {}", referenceId);

            UUID providerNotificationId = sendEmailResponse.getNotificationId();
            updateNotificationStatus(caseNotification, NotificationStatus.SUBMITTED, providerNotificationId);
            
            checkNotificationStatus(providerNotificationId.toString())
                .exceptionally(throwable -> {
                    log.error("Error checking notification status: {}", throwable.getMessage(), throwable);
                    return null;
                });

            return sendEmailResponse;
        } catch (NotificationClientException notificationClientException) {
            updateNotificationStatus(caseNotification, NotificationStatus.TECHNICAL_FAILURE, null);
            
            log.error("Failed to send email. Reference ID: {}. Reason: {}",
                      referenceId,
                      notificationClientException.getMessage(),
                      notificationClientException
            );

            throw new NotificationException("Email failed to send, please try again.", notificationClientException);
        }
    }

    @Async("notifyTaskExecutor")
    public CompletableFuture<Notification> checkNotificationStatus(String notificationId) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return getAndProcessNotificationStatus(notificationId);
            } catch (NotificationClientException | InterruptedException e) {
                handleStatusCheckException(notificationId, e);
                throw new CompletionException(e);
            }
        });
    }

    CaseNotification createCaseNotification(String recipient, NotificationType type, UUID caseId) {
        CaseNotification toSaveNotification = new CaseNotification();
        toSaveNotification.setCaseId(caseId);
        toSaveNotification.setStatus(NotificationStatus.PENDING_SCHEDULE);
        toSaveNotification.setType(type);
        toSaveNotification.setRecipient(recipient);

        try {
            CaseNotification savedNotification = notificationRepository.save(toSaveNotification);
            log.info(
                "Case Notification with ID {} has been saved to the database", savedNotification.getNotificationId()
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
    
    private Notification getAndProcessNotificationStatus(String notificationId) 
            throws NotificationClientException, InterruptedException {
        TimeUnit.MILLISECONDS.sleep(statusCheckDelay);
        
        Notification notification = notificationClient.getNotificationById(notificationId);
        
        updateNotificationStatusInDatabase(notification, notificationId);
        
        return notification;
    }
    
    private void handleStatusCheckException(String notificationId, Exception e) {
        log.error("Error checking notification status for ID: {}. Error: {}", 
            notificationId, 
            e.getMessage(),
            e
        );
        if (e instanceof InterruptedException) {
            Thread.currentThread().interrupt();
        }
    }

    private void updateNotificationStatusInDatabase(Notification notification, String notificationId) {
        try {
            CaseNotification caseNotification = notificationRepository
                .findByProviderNotificationId(UUID.fromString(notificationId))
                .orElse(null);
            
            if (caseNotification != null) {
                updateNotificationWithStatus(caseNotification, notification.getStatus());
            } else {
                log.warn("Could not find case notification with provider ID: {}", notificationId);
            }
        } catch (Exception e) {
            log.error("Error updating notification status in database for ID: {}. Error: {}", 
                notificationId, e.getMessage(), e);
        }
    }
    
    private void updateNotificationWithStatus(CaseNotification caseNotification, String status) {
        try {
            NotificationStatus notificationStatus = NotificationStatus.fromApiValue(status);
            updateNotificationStatus(caseNotification, notificationStatus, null);
        } catch (IllegalArgumentException e) {
            log.warn("Unknown notification status: {}", status);
        }
    }

    private Optional<CaseNotification> updateNotificationStatus(
            CaseNotification notification, 
            NotificationStatus status, 
            UUID providerNotificationId) {
        
        try {
            notification.setStatus(status);
            notification.setLastUpdatedAt(Instant.now());
            
            if (providerNotificationId != null) {
                notification.setProviderNotificationId(providerNotificationId);
            }
            
            if (status == NotificationStatus.SENDING) {
                notification.setSubmittedAt(Instant.now());
            }
            
            CaseNotification saved = notificationRepository.save(notification);
            log.info("Updated notification status to {} for notification ID: {}", 
                    status, notification.getNotificationId());
            return Optional.of(saved);
        } catch (Exception e) {
            log.error("Error updating notification status to {}: {}", 
                    status, e.getMessage(), e);
            return Optional.empty();
        }
    }
}
