package uk.gov.hmcts.reform.enforcement.notify.task;

import com.github.kagkarlsson.scheduler.task.CompletionHandler;
import com.github.kagkarlsson.scheduler.task.FailureHandler;
import com.github.kagkarlsson.scheduler.task.SchedulableInstance;
import com.github.kagkarlsson.scheduler.task.TaskDescriptor;
import com.github.kagkarlsson.scheduler.task.TaskInstance;
import com.github.kagkarlsson.scheduler.task.helper.CustomTask;
import com.github.kagkarlsson.scheduler.task.helper.Tasks;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.reform.enforcement.notify.config.NotificationErrorHandler;
import uk.gov.hmcts.reform.enforcement.notify.entities.CaseNotification;
import uk.gov.hmcts.reform.enforcement.notify.exception.PermanentNotificationException;
import uk.gov.hmcts.reform.enforcement.notify.exception.TemporaryNotificationException;
import uk.gov.hmcts.reform.enforcement.notify.model.EmailState;
import uk.gov.hmcts.reform.enforcement.notify.repository.NotificationRepository;
import uk.gov.hmcts.reform.enforcement.notify.service.NotificationService;
import uk.gov.service.notify.NotificationClient;
import uk.gov.service.notify.NotificationClientException;
import uk.gov.service.notify.SendEmailResponse;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static uk.gov.hmcts.reform.enforcement.notify.task.VerifyEmailTaskComponent.verifyEmailTask;

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
    private final Duration statusCheckTaskDelay;

    @Autowired
    public SendEmailTaskComponent(
        NotificationService notificationService,
        NotificationClient notificationClient,
        NotificationErrorHandler errorHandler,
        NotificationRepository notificationRepository,
        @Value("${notify.send-email.max-retries:5}") int maxRetriesSendEmail,
        @Value("${notify.send-email.backoff-delay-seconds:300s}") Duration sendingBackoffDelay,
        @Value("${notify.task-processing-delay-seconds:2s}") Duration processingDelay,
        @Value("${notify.check-status.task-delay-seconds:60s}") Duration statusCheckTaskDelay
    ) {
        this.notificationService = notificationService;
        this.notificationClient = notificationClient;
        this.errorHandler = errorHandler;
        this.notificationRepository = notificationRepository;
        this.maxRetriesSendEmail = maxRetriesSendEmail;
        this.sendingBackoffDelay = sendingBackoffDelay;
        this.processingDelay = processingDelay;
        this.statusCheckTaskDelay = statusCheckTaskDelay;
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

                CaseNotification caseNotification = notificationOpt.get();

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

                    if (response.getNotificationId() == null) {
                        log.error("Email service returned null notification ID for task: {}", emailState.getId());
                        throw new PermanentNotificationException("Null notification ID from email service",
                                                                 new IllegalStateException(
                                                                     "Email service returned null notification ID"));
                    }

                    notificationService.updateNotificationAfterSending(
                        emailState.getDbNotificationId(),
                        response.getNotificationId()
                    );

                    String notificationId = response.getNotificationId().toString();
                    log.info("Request sent successfully. Notification ID: {}", notificationId);

                    EmailState nextState = emailState.toBuilder()
                        .notificationId(notificationId)
                        .build();

                    return new CompletionHandler.OnCompleteReplace<>(
                        currentInstance -> SchedulableInstance.of(
                            new TaskInstance<>(
                                verifyEmailTask.getTaskName(),
                                currentInstance.getId(), 
                                nextState
                            ),
                            Instant.now().plus(statusCheckTaskDelay)
                        )
                    );
                } catch (NotificationClientException e) {
                    log.error("NotificationClient error sending email: {}", e.getMessage(), e);

                    if (isPermanentFailure(e)) {
                        errorHandler.handleSendEmailException(
                            e,
                            caseNotification,
                            UUID.randomUUID().toString(),
                            this::updateNotificationFromStatusUpdate
                        );
                        
                        String dummyNotificationId = UUID.randomUUID().toString();
                        EmailState nextState = emailState.toBuilder()
                            .notificationId(dummyNotificationId)
                            .build();
                            
                        return new CompletionHandler.OnCompleteReplace<>(
                            currentInstance -> SchedulableInstance.of(
                                new TaskInstance<>(
                                    verifyEmailTask.getTaskName(),
                                    currentInstance.getId(), 
                                    nextState
                                ),
                                Instant.now().plus(statusCheckTaskDelay)
                            )
                        );
                    } else {
                        throw new TemporaryNotificationException("Email temporarily failed to send.", e);
                    }
                }
            });
    }

    private void updateNotificationFromStatusUpdate(NotificationErrorHandler.NotificationStatusUpdate statusUpdate) {
        notificationService.updateNotificationStatus(
            statusUpdate.notification().getNotificationId(),
            statusUpdate.status().toString()
        );
    }

    private boolean isPermanentFailure(NotificationClientException e) {
        int httpStatusCode = e.getHttpResult();
        return httpStatusCode == 400 || httpStatusCode == 403;
    }
}
