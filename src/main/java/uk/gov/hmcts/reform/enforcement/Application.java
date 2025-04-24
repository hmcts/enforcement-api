package uk.gov.hmcts.reform.enforcement;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(
    scanBasePackages = {
        "uk.gov.hmcts.reform.enforcement",
        "uk.gov.hmcts.ccd.sdk",
    }
)
@SuppressWarnings("HideUtilityClassConstructor")
public class Application {
    public static void main(final String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
