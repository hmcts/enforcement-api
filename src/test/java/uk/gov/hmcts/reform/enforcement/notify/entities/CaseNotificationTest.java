package uk.gov.hmcts.reform.enforcement.notify.entities;

import org.junit.jupiter.api.Test;
import uk.gov.hmcts.reform.enforcement.notify.model.NotificationStatus;
import uk.gov.hmcts.reform.enforcement.notify.model.NotificationType;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class CaseNotificationTest {

    @Test
    void testEntityCreation() {

        UUID notificationId = UUID.randomUUID();
        UUID caseId = UUID.randomUUID();
        UUID providerNotificationId = UUID.randomUUID();
        LocalDateTime now = LocalDateTime.now();
        
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
        
        assertEquals(notificationId, notification.getNotificationId());
        assertEquals(caseId, notification.getCaseId());
        assertEquals(providerNotificationId, notification.getProviderNotificationId());
        assertEquals(now, notification.getSubmittedAt());
        assertEquals(now, notification.getScheduledAt());
        assertEquals(now, notification.getLastUpdatedAt());
        assertEquals(NotificationStatus.DELIVERED, notification.getStatus());
        assertEquals(NotificationType.EMAIL, notification.getType());
        assertEquals("test@example.com", notification.getRecipient());
    }
    
    @Test
    void testPrePersist() {

        CaseNotification notification = new CaseNotification();
        
        notification.prePersist();
        
        assertNotNull(notification.getLastUpdatedAt());
    }
    
    @Test
    void testPreUpdate() {

        CaseNotification notification = new CaseNotification();
        LocalDateTime oldDate = LocalDateTime.now().minusDays(1);
        notification.setLastUpdatedAt(oldDate);
        
        notification.preUpdate();
        
        assertNotNull(notification.getLastUpdatedAt());
    }
}
