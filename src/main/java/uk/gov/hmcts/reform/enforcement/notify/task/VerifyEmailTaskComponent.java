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
import org.springframework.stereotype.Component;
import uk.gov.hmcts.reform.enforcement.notify.config.NotificationErrorHandler;
import uk.gov.hmcts.reform.enforcement.notify.model.EmailState;
import uk.gov.hmcts.reform.enforcement.notify.service.NotificationService;
import uk.gov.service.notify.Notification;
import uk.gov.service.notify.NotificationClient;
import uk.gov.service.notify.NotificationClientException;

import java.time.Duration;
import java.util.Objects;

import static uk.gov.hmcts.reform.enforcement.notify.model.NotificationStatus.DELIVERED;
import static uk.gov.hmcts.reform.enforcement.notify.model.NotificationStatus.PERMANENT_FAILURE;

@Component
@Slf4j
public class VerifyEmailTaskComponent {
    private static final String VERIFY_EMAIL_TASK_NAME = "verify-email-task";

    public static final TaskDescriptor<EmailState> verifyEmailTask =
        TaskDescriptor.of(VERIFY_EMAIL_TASK_NAME, EmailState.class);

    private final NotificationService notificationService;
    private final NotificationClient notificationClient;
    private final NotificationErrorHandler errorHandler;
    private final int maxRetriesCheckEmail;
    private final Duration statusCheckBackoffDelay;

    @Autowired
    public VerifyEmailTaskComponent(
        NotificationService notificationService,
        NotificationClient notificationClient,
        NotificationErrorHandler errorHandler,
        @Value("${notify.check-status.max-retries:5}") int maxRetriesCheckEmail,
        @Value("${notify.check-status.backoff-delay-seconds:3600s}") Duration statusCheckBackoffDelay
    ) {
        this.notificationService = notificationService;
        this.notificationClient = notificationClient;
        this.errorHandler = errorHandler;
        this.maxRetriesCheckEmail = maxRetriesCheckEmail;
        this.statusCheckBackoffDelay = statusCheckBackoffDelay;
    }

    @Bean
    public CustomTask<EmailState> verifyEmailTask() {
        return Tasks.custom(verifyEmailTask)
            .onFailure(new FailureHandler.MaxRetriesFailureHandler<>(
                maxRetriesCheckEmail,
                new FailureHandler.ExponentialBackoffFailureHandler<>(statusCheckBackoffDelay)
            ))
            .execute((taskInstance, executionContext) -> {
                EmailState emailState = taskInstance.getData();
                log.info("Verifying email delivery for ID: {}", emailState.getNotificationId());

                try {
                    Notification notification = notificationClient.getNotificationById(emailState.getNotificationId());

                    if (Objects.equals(notification.getStatus().toLowerCase(), DELIVERED.toString())) {
                        notificationService.updateNotificationStatus(
                            emailState.getDbNotificationId(),
                            notification.getStatus()
                        );
                    } else {
                        notificationService.updateNotificationStatus(
                            emailState.getDbNotificationId(),
                            PERMANENT_FAILURE.toString()
                        );
                    }

                    String status = notification.getStatus();
                    if (DELIVERED.toString().equalsIgnoreCase(status)) {
                        log.info("Email successfully delivered: {}", emailState.getId());
                    } else {
                        log.error("Failure with status: {} for task: {}", status, emailState.getId());
                    }
                    return new CompletionHandler.OnCompleteRemove<>();
                } catch (NotificationClientException e) {
                    log.error("Failed to verify status due to API error", e);
                    
                    try {
                        errorHandler.handleFetchException(
                            e, 
                            emailState.getNotificationId(), 
                            emailState.getDbNotificationId()
                        );
                    } catch (Exception ex) {
                        log.error("Error while handling notification exception: {}", ex.getMessage(), ex);
                    }
                    return new CompletionHandler.OnCompleteRemove<>();
                }
            });
    }
}
