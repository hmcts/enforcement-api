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

import static org.assertj.core.api.Assertions.assertThat;

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
        notification.setType(NotificationType.EMAIL.toString());
        notification.setRecipient("test@example.com");

        repository.save(notification);

        Optional<CaseNotification> found = repository.findByProviderNotificationId(providerNotificationId);

        assertThat(found).isPresent();
        assertThat(found.get().getProviderNotificationId()).isEqualTo(providerNotificationId);
        assertThat(found.get().getCaseId()).isEqualTo(caseId);
        assertThat(found.get().getStatus()).isEqualTo(NotificationStatus.SUBMITTED);
    }

    @Test
    void testFindByProviderNotificationIdWhenNotFound() {

        Optional<CaseNotification> found = repository.findByProviderNotificationId(UUID.randomUUID());

        assertThat(found).isEmpty();
    }
}
