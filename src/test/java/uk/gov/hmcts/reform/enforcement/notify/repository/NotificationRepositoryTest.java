package uk.gov.hmcts.reform.enforcement.notify.repository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;
import uk.gov.hmcts.reform.enforcement.notify.entities.CaseNotification;
import uk.gov.hmcts.reform.enforcement.notify.model.NotificationStatus;
import uk.gov.hmcts.reform.enforcement.notify.model.NotificationType;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class NotificationRepositoryTest {

    @Autowired
    private NotificationRepository repository;
    
    @Test
    void testFindByProviderNotificationId() {
        
        UUID caseId = UUID.randomUUID();
        UUID providerNotificationId = UUID.randomUUID();
        
        CaseNotification notification = new CaseNotification();
        notification.setCaseId(caseId);
        notification.setProviderNotificationId(providerNotificationId);
        notification.setStatus(NotificationStatus.SUBMITTED);
        notification.setType(NotificationType.EMAIL);
        notification.setRecipient("test@example.com");
        
        repository.save(notification);
        
        Optional<CaseNotification> found = repository.findByProviderNotificationId(providerNotificationId);
        
        assertTrue(found.isPresent());
        assertEquals(providerNotificationId, found.get().getProviderNotificationId());
        assertEquals(caseId, found.get().getCaseId());
        assertEquals(NotificationStatus.SUBMITTED, found.get().getStatus());
    }
    
    @Test
    void testFindByProviderNotificationIdWhenNotFound() {
        
        Optional<CaseNotification> found = repository.findByProviderNotificationId(UUID.randomUUID());
        
        assertTrue(found.isEmpty());
    }
}
