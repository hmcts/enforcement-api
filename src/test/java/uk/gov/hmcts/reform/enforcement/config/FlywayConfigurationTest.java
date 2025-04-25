package uk.gov.hmcts.reform.enforcement.config;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import uk.gov.hmcts.reform.enforcement.data.migration.FlywayNoOpStrategy;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class FlywayConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
        .withUserConfiguration(FlywayConfiguration.class)
        .withBean(Flyway.class, () -> Flyway.configure().load());

    @Test
    void shouldReturnFlywayNoOpStrategyWhenNoopStrategyIsNotSpecified() {
        contextRunner
            .run(context -> {
                assertThat(context).hasSingleBean(FlywayMigrationStrategy.class);
                assertThat(context.getBean(FlywayMigrationStrategy.class))
                    .isInstanceOf(FlywayNoOpStrategy.class);
            });
    }

    @Test
    void shouldReturnFlywayNoOpStrategyWhenNoopStrategyIsTrue() {
        contextRunner
            .withPropertyValues("flyway.noop.strategy=true")
            .run(context -> {
                assertThat(context).hasSingleBean(FlywayMigrationStrategy.class);
                assertThat(context.getBean(FlywayMigrationStrategy.class))
                    .isInstanceOf(FlywayNoOpStrategy.class);
            });
    }

    @Test
    void shouldReturnNullStrategyWhenNoopStrategyIsFalse() {
        contextRunner
            .withPropertyValues("flyway.noop.strategy=false")
            .run(context -> {
                assertThat(context).doesNotHaveBean(FlywayMigrationStrategy.class);
            });
    }

    @Test
    void shouldNotRegisterBeansWhenFlywayIsDisabled() {
        new ApplicationContextRunner()
            .withUserConfiguration(FlywayConfiguration.class)
            .withBean(Flyway.class, () -> Flyway.configure().load())
            .withPropertyValues("spring.flyway.enabled=false")
            .run(context -> {
                assertThat(context).doesNotHaveBean(FlywayMigrationStrategy.class);
            });
    }
}
