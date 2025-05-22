package uk.gov.hmcts.reform.enforcement.notify.entities;

import org.junit.jupiter.api.Test;
import uk.gov.hmcts.reform.enforcement.notify.model.NotificationStatus;
import uk.gov.hmcts.reform.enforcement.notify.model.NotificationType;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CaseNotificationTest {

    @Test
    void testEntityCreation() {
        UUID notificationId = UUID.randomUUID();
        UUID caseId = UUID.randomUUID();
        UUID providerNotificationId = UUID.randomUUID();
        Instant now = Instant.now();
        
        CaseNotification notification = new CaseNotification();
        notification.setNotificationId(notificationId);
        notification.setCaseId(caseId);
        notification.setProviderNotificationId(providerNotificationId);
        notification.setSubmittedAt(now);
        notification.setScheduledAt(now);
        notification.setLastUpdatedAt(now);
        notification.setStatus(NotificationStatus.DELIVERED);
        notification.setType(NotificationType.EMAIL);
        notification.setRecipient("test@example.com");
        
        assertThat(notification.getNotificationId()).isEqualTo(notificationId);
        assertThat(notification.getCaseId()).isEqualTo(caseId);
        assertThat(notification.getProviderNotificationId()).isEqualTo(providerNotificationId);
        assertThat(notification.getSubmittedAt()).isEqualTo(now);
        assertThat(notification.getScheduledAt()).isEqualTo(now);
        assertThat(notification.getLastUpdatedAt()).isEqualTo(now);
        assertThat(notification.getStatus()).isEqualTo(NotificationStatus.DELIVERED);
        assertThat(notification.getType()).isEqualTo(NotificationType.EMAIL);
        assertThat(notification.getRecipient()).isEqualTo("test@example.com");
    }
    
    @Test
    void testPrePersist() {
        CaseNotification notification = new CaseNotification();
        
        notification.prePersist();
        
        assertThat(notification.getLastUpdatedAt()).isNotNull();
    }
    
    @Test
    void testPreUpdate() {
        CaseNotification notification = new CaseNotification();
        Instant oldDate = Instant.now().minusSeconds(86400); // One day ago
        notification.setLastUpdatedAt(oldDate);
        
        notification.preUpdate();
        
        assertThat(notification.getLastUpdatedAt()).isNotNull();
    }
}
