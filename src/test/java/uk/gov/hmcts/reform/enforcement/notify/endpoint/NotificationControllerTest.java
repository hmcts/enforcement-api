package uk.gov.hmcts.reform.enforcement.notify.endpoint;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.hmcts.reform.enforcement.notify.model.EmailNotificationRequest;
import uk.gov.hmcts.reform.enforcement.notify.service.NotificationService;
import uk.gov.service.notify.SendEmailResponse;

import java.net.URI;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationControllerTest {

    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private NotifyController notifyController;

    @Test
    void testSendEmailSuccess() {
        var notificationId = UUID.randomUUID();
        var templateId = UUID.randomUUID();
        var unsubscribeUrl = URI.create("https://unsubscribe.example.com");

        var emailRequest = new EmailNotificationRequest(
                "test@example.com",
                "templateId",
                null,
                "reference",
                "emailReplyToId"
        );

        SendEmailResponse mockResponse = mock(SendEmailResponse.class);
        when(mockResponse.getNotificationId()).thenReturn(notificationId);
        when(mockResponse.getReference()).thenReturn(Optional.of("reference"));
        when(mockResponse.getOneClickUnsubscribeURL()).thenReturn(Optional.of(unsubscribeUrl));
        when(mockResponse.getTemplateId()).thenReturn(templateId);
        when(mockResponse.getTemplateVersion()).thenReturn(1);
        when(mockResponse.getTemplateUri()).thenReturn("/template/uri");
        when(mockResponse.getBody()).thenReturn("Email body content");
        when(mockResponse.getSubject()).thenReturn("Email subject");
        when(mockResponse.getFromEmail()).thenReturn(Optional.of("noreply@example.com"));

        when(notificationService.sendEmail(any(EmailNotificationRequest.class))).thenReturn(mockResponse);

        var response = notifyController.sendEmail(
                "Bearer token",
                "ServiceAuthToken",
                emailRequest
        );

        assertThat(response.getBody())
                .extracting(
                        SendEmailResponse::getNotificationId,
                        SendEmailResponse::getReference,
                        SendEmailResponse::getOneClickUnsubscribeURL,
                        SendEmailResponse::getTemplateId,
                        SendEmailResponse::getTemplateVersion,
                        SendEmailResponse::getTemplateUri,
                        SendEmailResponse::getBody,
                        SendEmailResponse::getSubject,
                        SendEmailResponse::getFromEmail
                )
                .containsExactly(
                        notificationId,
                        Optional.of("reference"),
                        Optional.of(unsubscribeUrl),
                        templateId,
                        1,
                        "/template/uri",
                        "Email body content",
                        "Email subject",
                        Optional.of("noreply@example.com"));

        verify(notificationService).sendEmail(emailRequest);
    }
}
