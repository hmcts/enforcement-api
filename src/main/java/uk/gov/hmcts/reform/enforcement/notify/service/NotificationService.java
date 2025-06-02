package uk.gov.hmcts.reform.enforcement.notify.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.scheduling.annotation.Async;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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
        log.info("NotificationService initialised with repository: {}", notificationRepository);
    }

    @Transactional
    public SendEmailResponse sendEmail(EmailNotificationRequest emailRequest) {
        SendEmailResponse sendEmailResponse;
        final String destinationAddress = emailRequest.getEmailAddress();
        final String templateId = emailRequest.getTemplateId();
        final Map<String, Object> personalisation = emailRequest.getPersonalisation();
        final String referenceId = UUID.randomUUID().toString();

        log.info("Preparing to send email. Template ID: {}, Destination: {}, Reference: {}", 
            templateId, destinationAddress, referenceId);

        UUID caseId = UUID.randomUUID();
        log.info("Creating notification for case ID: {}", caseId);
        
        CaseNotification caseNotification = createCaseNotification(
            emailRequest.getEmailAddress(), 
            NotificationType.EMAIL, 
            caseId
        );
        
        if (caseNotification == null || caseNotification.getNotificationId() == null) {
            log.error("Failed to create notification record");
            throw new NotificationException("Failed to create notification record", null);
        }
        
        log.info("Created notification with ID: {}", caseNotification.getNotificationId());

        try {
            log.info("Sending email to GOV.UK Notify");
            sendEmailResponse = notificationClient.sendEmail(
                templateId,
                destinationAddress,
                personalisation,
                referenceId
            );

            log.info("Email sent successfully. Notify ID: {}, Reference ID: {}", 
                            sendEmailResponse.getNotificationId(), referenceId);

            UUID providerNotificationId = sendEmailResponse.getNotificationId();
            log.info("Updating notification status to SUBMITTED with provider ID: {}", providerNotificationId);
            
            Optional<CaseNotification> updated = updateNotificationStatus(
                caseNotification, 
                NotificationStatus.SUBMITTED, 
                providerNotificationId
            );
            
            if (updated.isEmpty()) {
                log.error("Failed to update notification status to SUBMITTED");
            } else {
                log.info("Successfully updated notification status to SUBMITTED");
            }
            
            checkNotificationStatus(providerNotificationId.toString())
                .exceptionally(throwable -> {
                    log.error("Error checking notification status: {}", throwable.getMessage(), throwable);
                    return null;
                });

            return sendEmailResponse;
        } catch (NotificationClientException notificationClientException) {
            log.error("Failed to send email. Reference ID: {}. Reason: {}",
                      referenceId,
                      notificationClientException.getMessage(),
                      notificationClientException
            );
            
            updateNotificationStatus(caseNotification, NotificationStatus.TECHNICAL_FAILURE, null);
            
            throw new NotificationException("Email failed to send, please try again.", notificationClientException);
        }
    }

    @Async("notifyTaskExecutor")
    public CompletableFuture<Notification> checkNotificationStatus(String notificationId) {
        log.info("Scheduled status check for notification ID: {}", notificationId);
        return CompletableFuture.supplyAsync(() -> {
            try {
                return getAndProcessNotificationStatus(notificationId);
            } catch (NotificationClientException | InterruptedException e) {
                handleStatusCheckException(notificationId, e);
                throw new CompletionException(e);
            }
        });
    }

    @Transactional
    public CaseNotification createCaseNotification(String recipient, NotificationType type, UUID caseId) {
        log.info("Creating case notification record. Case ID: {}, Recipient: {}, Type: {}", caseId, recipient, type);
        
        CaseNotification notification = new CaseNotification();
        notification.setNotificationId(UUID.randomUUID());
        notification.setCaseId(caseId);
        notification.setStatus(NotificationStatus.PENDING_SCHEDULE);
        notification.setType(type);
        notification.setRecipient(recipient);
        notification.setLastUpdatedAt(Instant.now());
        
        log.debug("Notification object created: {}", notification);

        try {
            log.info("Saving notification to database");
            CaseNotification savedNotification = notificationRepository.save(notification);
            log.info("Case Notification with ID {} has been saved to the database", 
                                            savedNotification.getNotificationId());
            return savedNotification;
        } catch (DataAccessException dataAccessException) {
            log.error(
                "Failed to save Case Notification with Case ID: {}. Error: {}",
                notification.getCaseId(),
                dataAccessException.getMessage(),
                dataAccessException
            );
            throw new NotificationException("Failed to save Case Notification.", dataAccessException);
        }
    }
    
    private Notification getAndProcessNotificationStatus(String notificationId) 
            throws NotificationClientException, InterruptedException {
        log.info("Sleeping for {} milliseconds before checking notification status", statusCheckDelay);
        TimeUnit.MILLISECONDS.sleep(statusCheckDelay);
        
        log.info("Checking notification status for ID: {}", notificationId);
        Notification notification = notificationClient.getNotificationById(notificationId);
        log.info("Got notification status: {} for ID: {}", notification.getStatus(), notificationId);
        
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

    @Transactional
    public void updateNotificationStatusInDatabase(Notification notification, String notificationId) {
        try {
            log.info("Looking up notification in database with provider ID: {}", notificationId);
            CaseNotification caseNotification = notificationRepository
                .findByProviderNotificationId(UUID.fromString(notificationId))
                .orElse(null);
            
            if (caseNotification != null) {
                log.info("Found notification in database. Updating status to: {}", notification.getStatus());
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
            log.info("Converting API status '{}' to enum status '{}'", status, notificationStatus);
            updateNotificationStatus(caseNotification, notificationStatus, null);
        } catch (IllegalArgumentException e) {
            log.warn("Unknown notification status: {}", status);
        }
    }

    @Transactional
    public Optional<CaseNotification> updateNotificationStatus(
            CaseNotification notification, 
            NotificationStatus status, 
            UUID providerNotificationId) {
        
        log.info("Updating notification ID: {} to status: {}", notification.getNotificationId(), status);
        
        try {
            notification.setStatus(status);
            notification.setLastUpdatedAt(Instant.now());
            
            if (providerNotificationId != null) {
                notification.setProviderNotificationId(providerNotificationId);
            }
            
            if (status == NotificationStatus.SENDING) {
                notification.setSubmittedAt(Instant.now());
            }
            
            log.debug("Saving save notification: {}", notification);
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
