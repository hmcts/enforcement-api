package uk.gov.hmcts.reform.enforcement.config;

import io.swagger.v3.oas.models.OpenAPI;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest
class OpenAPIConfigurationTest {

    @Autowired
    private ApplicationContext context;

    @Test
    void shouldCreateOpenAPIBean() {
        OpenAPI openAPI = context.getBean(OpenAPI.class);

        assertNotNull(openAPI);

        assertNotNull(openAPI.getInfo());
        assertEquals("Enforcement API", openAPI.getInfo().getTitle());
        assertEquals("Enforcement API", openAPI.getInfo().getDescription());
        assertEquals("v0.0.1", openAPI.getInfo().getVersion());

        assertNotNull(openAPI.getInfo().getLicense());
        assertEquals("MIT", openAPI.getInfo().getLicense().getName());
        assertEquals("https://opensource.org/licenses/MIT", openAPI.getInfo().getLicense().getUrl());

        assertNotNull(openAPI.getExternalDocs());
        assertEquals("README", openAPI.getExternalDocs().getDescription());
        assertEquals("https://github.com/hmcts/enforcement-api", openAPI.getExternalDocs().getUrl());
    }
}
