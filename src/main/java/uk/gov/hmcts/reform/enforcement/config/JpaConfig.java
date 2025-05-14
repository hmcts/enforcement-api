package uk.gov.hmcts.reform.enforcement.config;

import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@Configuration
@EntityScan("uk.gov.hmcts.reform.enforcement.notify.domain")
@EnableJpaRepositories("uk.gov.hmcts.reform.enforcement.notify.repository")
@EnableTransactionManagement
public class JpaConfig {
}
