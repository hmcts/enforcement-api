package uk.gov.hmcts.reform.enforcement.demo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@SuppressWarnings("HideUtilityClassConstructor") // Spring needs a constructor, its not a utility class
public class EnforcementApplication {

    public static void main(final String[] args) {
        SpringApplication.run(EnforcementApplication.class, args);
    }
}
