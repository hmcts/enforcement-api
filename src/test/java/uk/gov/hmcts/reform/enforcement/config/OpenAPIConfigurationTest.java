package uk.gov.hmcts.reform.enforcement.config;

import static org.assertj.core.api.Assertions.assertThat;

import io.swagger.v3.oas.models.OpenAPI;
import org.junit.jupiter.api.Test;

class OpenAPIConfigurationTest {

    @Test
    void shouldReturnOpenAPIWithExpectedProperties() {
        OpenAPIConfiguration config = new OpenAPIConfiguration();
        OpenAPI openAPI = config.openAPI();

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
