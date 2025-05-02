package uk.gov.hmcts.reform.enforcement.notify.service;

import java.util.Map;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.reform.enforcement.notify.domain.CaseNotification;
import uk.gov.hmcts.reform.enforcement.notify.exception.NotificationException;
import uk.gov.hmcts.reform.enforcement.notify.model.EmailNotificationRequest;
import uk.gov.hmcts.reform.enforcement.notify.model.NotificationStatus;
import uk.gov.hmcts.reform.enforcement.notify.repository.NotificationRepository;
import uk.gov.service.notify.NotificationClient;
import uk.gov.service.notify.NotificationClientException;
import uk.gov.service.notify.SendEmailResponse;

@Service
@Slf4j
public class NotificationService {
    private final NotificationClient notificationClient;
    private final NotificationRepository notificationRepository;

    public NotificationService(
        NotificationClient notificationClient,
        NotificationRepository notificationRepository) {
        this.notificationClient = notificationClient;
        this.notificationRepository = notificationRepository;
    }

    public SendEmailResponse sendEmail(EmailNotificationRequest emailRequest) {
        SendEmailResponse sendEmailResponse;
        final String destinationAddress = emailRequest.getEmailAddress();
        final String templateId = emailRequest.getTemplateId();
        final Map<String, Object> personalisation = emailRequest.getPersonalisation();
        final String referenceId = UUID.randomUUID().toString();

        // Save notification to database
        createCaseNotification(emailRequest.getEmailAddress(), "Email", UUID.randomUUID());

        try {
            sendEmailResponse = notificationClient.sendEmail(
                templateId,
                destinationAddress,
                personalisation,
                referenceId
            );

            log.debug("Email sent successfully. Reference ID: {}", referenceId);

            return sendEmailResponse;
        } catch (NotificationClientException notificationClientException) {
            log.error("Failed to send email. Reference ID: {}. Reason: {}",
                      referenceId,
                      notificationClientException.getMessage(),
                      notificationClientException
            );

            throw new NotificationException("Email failed to send, please try again.", notificationClientException);
        }
    }


    CaseNotification createCaseNotification(String recipient, String type, UUID caseId) {
        CaseNotification toSaveNotification = new CaseNotification();
        toSaveNotification.setCaseId(caseId);
        // Use the toString() method of the enum to get the string value
        toSaveNotification.setStatus(NotificationStatus.PENDING_SCHEDULE.toString());
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
}
