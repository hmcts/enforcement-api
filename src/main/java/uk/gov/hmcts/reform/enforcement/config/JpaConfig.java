package uk.gov.hmcts.reform.enforcement.config;

import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@Configuration
@EnableJpaRepositories(basePackages = "uk.gov.hmcts.reform.enforcement.notify.repository")
@EntityScan(basePackages = {
    "uk.gov.hmcts.reform.enforcement.notify.entities"
})
public class JpaConfig {
}
