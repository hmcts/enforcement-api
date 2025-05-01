package uk.gov.hmcts.reform.enforcement;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import uk.gov.hmcts.reform.idam.client.IdamApi;

@SpringBootApplication(
    scanBasePackages = {
        "uk.gov.hmcts.reform.enforcement",
        "uk.gov.hmcts.ccd.sdk"
    })
@SuppressWarnings("HideUtilityClassConstructor") // Spring needs a constructor, its not a utility class
@EnableFeignClients(
    clients = {
        IdamApi.class
    }
)
public class Application {

    public static void main(final String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
