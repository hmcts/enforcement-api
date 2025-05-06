package uk.gov.hmcts.reform.enforcement.notify.repository;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import uk.gov.hmcts.reform.enforcement.notify.domain.CaseNotification;

public interface NotificationRepository extends JpaRepository<CaseNotification, UUID> {
}
