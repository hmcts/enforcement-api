package uk.gov.hmcts.reform.enforcement.testingsupport.endpoint;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import uk.gov.hmcts.reform.enforcement.notify.model.EmailNotificationRequest;
import uk.gov.hmcts.reform.enforcement.notify.model.EmailNotificationResponse;
import uk.gov.hmcts.reform.enforcement.notify.service.NotificationService;

import static org.springframework.http.HttpHeaders.AUTHORIZATION;

@Slf4j
@RestController
@RequestMapping("/testing-support")
@ConditionalOnProperty(name = "testing-support.enabled", havingValue = "true")
public class NotifyController {

    private final NotificationService notificationService;

    public NotifyController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @PostMapping(value = "/send-email", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<EmailNotificationResponse> sendEmail(
        @RequestHeader(value = AUTHORIZATION, defaultValue = "DummyId") String authorisation,
        @RequestHeader(value = "ServiceAuthorization") String serviceAuthorization,
        @RequestBody EmailNotificationRequest emailRequest) {

        log.info("Received request to send email to: {}", emailRequest.getEmailAddress());

        try {
            EmailNotificationResponse response = notificationService.scheduleEmailNotification(emailRequest);

            log.info("Email notification scheduled successfully with task ID: {}", response.getTaskId());
            return ResponseEntity.ok().body(response);

        } catch (Exception e) {
            log.error("Failed to schedule email notification: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }
}
