package uk.gov.hmcts.reform.enforcement.testingsupport.endpoint;

import com.github.kagkarlsson.scheduler.SchedulerClient;
import com.github.kagkarlsson.scheduler.task.Task;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import uk.gov.hmcts.reform.enforcement.notify.model.EmailNotificationRequest;
import uk.gov.hmcts.reform.enforcement.notify.service.NotificationService;
import uk.gov.service.notify.SendEmailResponse;

import java.time.Instant;
import java.util.UUID;

import static org.springframework.http.HttpHeaders.AUTHORIZATION;

/*
    This is a temporary endpoint created purely for testing the integration with Gov Notify, and will be removed once
    events are added to our service, DO NOT USE for any future events.
*/

@Slf4j
@RestController
@RequestMapping("/testing-support")
@ConditionalOnProperty(name = "testing-support.enabled", havingValue = "true")
@Tag(name = "Testing Support")
public class TestingSupportController {

    private final NotificationService notificationService;
    private final SchedulerClient schedulerClient;
    private final Task<Void> helloWorldTask;

    public TestingSupportController(
        NotificationService notificationService,
        SchedulerClient schedulerClient,
        @Qualifier("helloWorldTask") Task<Void> helloWorldTask
    ) {
        this.notificationService = notificationService;
        this.schedulerClient = schedulerClient;
        this.helloWorldTask = helloWorldTask;
    }

    @PostMapping(value = "/send-email", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<SendEmailResponse> sendEmail(
        @RequestHeader(value = AUTHORIZATION, defaultValue = "DummyId") String authorisation,
        @RequestHeader(value = "ServiceAuthorization") String serviceAuthorization,
        @RequestBody EmailNotificationRequest emailRequest) {
        log.debug("Received request to send email to {}", emailRequest.getEmailAddress());

        SendEmailResponse notificationResponse = notificationService.sendEmail(emailRequest);

        return ResponseEntity.ok(notificationResponse);
    }

    @PostMapping("/create-sample-job")
    @Operation(summary = "Create a sample DB Scheduler job",
        description = "Schedules a sample job that will execute after the specified delay in seconds")
    @ApiResponse(responseCode = "200", description = "Sample job scheduled successfully")
    @ApiResponse(responseCode = "500", description = "Failed to schedule sample job")
    public ResponseEntity<String> createSampleJob(
        @RequestParam(value = "delaySeconds", defaultValue = "1") int delaySeconds,
        @RequestHeader(value = AUTHORIZATION, defaultValue = "DummyId") String authorisation,
        @RequestHeader(value = "ServiceAuthorization") String serviceAuthorization) {
        try {
            String taskId = "helloWorld-" + UUID.randomUUID();
            Instant executionTime = Instant.now().plusSeconds(delaySeconds);

            schedulerClient.scheduleIfNotExists(
                helloWorldTask.instance(taskId),
                executionTime
            );

            log.info("Scheduled Hello World task with ID: {} to execute at: {}", taskId, executionTime);
            return ResponseEntity.ok(String.format(
                "Hello World task scheduled successfully with ID: %s, execution time: %s",
                taskId, executionTime));
        } catch (Exception e) {
            log.error("Failed to schedule Hello World task", e);
            return ResponseEntity.internalServerError()
                .body("An error occurred while scheduling the Hello World task.");
        }
    }
}
