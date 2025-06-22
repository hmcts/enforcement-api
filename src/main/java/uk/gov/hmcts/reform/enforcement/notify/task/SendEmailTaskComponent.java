package uk.gov.hmcts.reform.enforcement.notify.task;

import com.github.kagkarlsson.scheduler.task.CompletionHandler;
import com.github.kagkarlsson.scheduler.task.FailureHandler;
import com.github.kagkarlsson.scheduler.task.TaskDescriptor;
import com.github.kagkarlsson.scheduler.task.helper.CustomTask;
import com.github.kagkarlsson.scheduler.task.helper.Tasks;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.reform.enforcement.notify.config.NotificationErrorHandler;
import uk.gov.hmcts.reform.enforcement.notify.entities.CaseNotification;
import uk.gov.hmcts.reform.enforcement.notify.model.EmailState;
import uk.gov.hmcts.reform.enforcement.notify.repository.NotificationRepository;
import uk.gov.hmcts.reform.enforcement.notify.service.NotificationService;
import uk.gov.service.notify.NotificationClient;
import uk.gov.service.notify.NotificationClientException;
import uk.gov.service.notify.SendEmailResponse;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Component
@Slf4j
public class SendEmailTaskComponent {
    private static final String SEND_EMAIL_TASK_NAME = "send-email-task";

    public static final TaskDescriptor<EmailState> sendEmailTask =
        TaskDescriptor.of(SEND_EMAIL_TASK_NAME, EmailState.class);

    private final NotificationService notificationService;
    private final NotificationClient notificationClient;
    private final NotificationErrorHandler errorHandler;
    private final NotificationRepository notificationRepository;
    private final int maxRetriesSendEmail;
    private final Duration sendingBackoffDelay;
    private final Duration processingDelay;

    @Autowired
    public SendEmailTaskComponent(
        NotificationService notificationService,
        NotificationClient notificationClient,
        NotificationErrorHandler errorHandler,
        NotificationRepository notificationRepository,
        @Value("${notify.send-email.max-retries:5}") int maxRetriesSendEmail,
        @Value("${notify.send-email.backoff-delay-seconds:300s}") Duration sendingBackoffDelay,
        @Value("${notify.task-processing-delay-seconds:2s}") Duration processingDelay
    ) {
        this.notificationService = notificationService;
        this.notificationClient = notificationClient;
        this.errorHandler = errorHandler;
        this.notificationRepository = notificationRepository;
        this.maxRetriesSendEmail = maxRetriesSendEmail;
        this.sendingBackoffDelay = sendingBackoffDelay;
        this.processingDelay = processingDelay;
    }

    @Bean
    public CustomTask<EmailState> sendEmailTask() {
        return Tasks.custom(sendEmailTask)
            .onFailure(new FailureHandler.MaxRetriesFailureHandler<>(
                maxRetriesSendEmail,
                new FailureHandler.ExponentialBackoffFailureHandler<>(sendingBackoffDelay)
            ))
            .execute((taskInstance, executionContext) -> {
                EmailState emailState = taskInstance.getData();
                log.info("Processing send email task: {} with DB notification ID: {}",
                         emailState.getId(), emailState.getDbNotificationId());

                Optional<CaseNotification> notificationOpt = notificationRepository.findById(
                    emailState.getDbNotificationId());
                if (notificationOpt.isEmpty()) {
                    log.error("Notification not found with ID: {}", emailState.getDbNotificationId());
                    return new CompletionHandler.OnCompleteRemove<>();
                }

                try {
                    // Add a small delay per processing delay variable to ensure the task appears in scheduled_tasks for 1-3 seconds
                    Thread.sleep(processingDelay.toMillis());
                    
                    // Update to SUBMITTED status
                    notificationService.updateNotificationStatus(
                        emailState.getDbNotificationId(),
                        "submitted"
                    );
                    
                    // Send email in background
                    sendEmailInBackground(emailState);
                    
                    // Remove the task from scheduled_tasks
                    return new CompletionHandler.OnCompleteRemove<>();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    log.error("Task interrupted: {}", e.getMessage());
                    return new CompletionHandler.OnCompleteRemove<>();
                } catch (Exception e) {
                    log.error("Error in send email task: {}", e.getMessage(), e);
                    // Still update to SUBMITTED per acceptance criteria
                    notificationService.updateNotificationStatus(
                        emailState.getDbNotificationId(),
                        "submitted"
                    );
                    return new CompletionHandler.OnCompleteRemove<>();
                }
            });
    }

    @Async("notifyTaskExecutor")
    public void sendEmailInBackground(EmailState emailState) {
        try {
            final String templateId = emailState.getTemplateId();
            final String destinationAddress = emailState.getEmailAddress();
            final Map<String, Object> personalisation = emailState.getPersonalisation();
            final String referenceId = UUID.randomUUID().toString();

            SendEmailResponse response = notificationClient.sendEmail(
                templateId,
                destinationAddress,
                personalisation,
                referenceId
            );

            if (response.getNotificationId() != null) {
                notificationService.updateNotificationAfterSending(
                    emailState.getDbNotificationId(),
                    response.getNotificationId()
                );
                log.info("Request sent successfully. Notification ID: {}", response.getNotificationId());
            } else {
                log.error("Email service returned null notification ID for task: {}", emailState.getId());
                notificationService.updateNotificationAfterFailure(
                    emailState.getDbNotificationId(),
                    new IllegalStateException("Null notification ID from email service")
                );
            }
        } catch (NotificationClientException e) {
            log.error("NotificationClient error sending email: {}", e.getMessage(), e);
            // Both permanent and temporary failures get SUBMITTED status per Acceptance Criteria
            notificationService.updateNotificationAfterFailure(
                emailState.getDbNotificationId(),
                e
            );
        }
    }

    boolean isPermanentFailure(NotificationClientException e) {
        int httpStatusCode = e.getHttpResult();
        return httpStatusCode == 400 || httpStatusCode == 403;
    }
}
