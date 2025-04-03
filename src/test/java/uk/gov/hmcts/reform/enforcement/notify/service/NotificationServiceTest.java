package uk.gov.hmcts.reform.enforcement.notify.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.hmcts.reform.enforcement.notify.exception.NotificationException;
import uk.gov.hmcts.reform.enforcement.notify.model.EmailNotificationRequest;
import uk.gov.service.notify.NotificationClient;
import uk.gov.service.notify.NotificationClientException;
import uk.gov.service.notify.SendEmailResponse;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private NotificationClient notificationClient;

    @InjectMocks
    private NotificationService notificationService;

    @Test
    void testSendEmailSuccess() throws NotificationClientException {
        Map<String, Object> personalisation = Map.of("name", "John Doe");

        var emailRequest = new EmailNotificationRequest(
                "test@example.com",
                "templateId",
                personalisation,
                "reference",
                "emailReplyToId"
        );

        var sendEmailResponse = mock(SendEmailResponse.class);
        var expectedNotificationId = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");

        when(sendEmailResponse.getNotificationId()).thenReturn(expectedNotificationId);
        when(notificationClient.sendEmail(anyString(), anyString(), anyMap(), anyString()))
                .thenReturn(sendEmailResponse);

        var referenceCaptor = ArgumentCaptor.forClass(String.class);

        var response = notificationService.sendEmail(emailRequest);

        assertThat(response)
                .isNotNull()
                .extracting(SendEmailResponse::getNotificationId)
                .isEqualTo(expectedNotificationId);

        verify(notificationClient).sendEmail(
                eq("test@example.com"),
                eq("templateId"),
                eq(personalisation),
                referenceCaptor.capture()
        );

        assertThat(referenceCaptor.getValue()).isNotEmpty();
    }

    @Test
    void testSendEmailFailure() throws NotificationClientException {
        var emailRequest = new EmailNotificationRequest(
                "test@example.com",
                "templateId",
                new HashMap<>(),
                "reference",
                "emailReplyToId"
        );

        when(notificationClient.sendEmail(anyString(), anyString(), anyMap(), anyString()))
                .thenThrow(new NotificationClientException("Error"));

        assertThatThrownBy(() -> notificationService.sendEmail(emailRequest))
                .isInstanceOf(NotificationException.class)
                .hasMessage("Email failed to send, please try again.");

        verify(notificationClient)
                .sendEmail(anyString(), anyString(), anyMap(), anyString());
    }
}
