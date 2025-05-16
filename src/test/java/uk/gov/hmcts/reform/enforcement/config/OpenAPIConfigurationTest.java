package uk.gov.hmcts.reform.enforcement.config;

import io.swagger.v3.oas.models.OpenAPI;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = {TestDatabaseConfig.class})
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:testdb",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "notify.api-key=test-key"
})
class OpenAPIConfigurationTest {

    @Autowired
    private ApplicationContext context;

    @Test
    void shouldCreateOpenAPIBean() {
        OpenAPI openAPI = context.getBean(OpenAPI.class);

        assertThat(openAPI).isNotNull();
        assertThat(openAPI.getInfo()).isNotNull();

        assertThat(openAPI.getInfo().getTitle()).isEqualTo("Enforcement API");
        assertThat(openAPI.getInfo().getDescription()).isEqualTo("Enforcement API");
        assertThat(openAPI.getInfo().getVersion()).isEqualTo("v0.0.1");

        assertThat(openAPI.getInfo().getLicense()).isNotNull();
        assertThat(openAPI.getInfo().getLicense().getName()).isEqualTo("MIT");
        assertThat(openAPI.getInfo().getLicense().getUrl()).isEqualTo("https://opensource.org/licenses/MIT");

        assertThat(openAPI.getExternalDocs()).isNotNull();
        assertThat(openAPI.getExternalDocs().getDescription()).isEqualTo("README");
        assertThat(openAPI.getExternalDocs().getUrl()).isEqualTo("https://github.com/hmcts/enforcement-api");
    }
}
