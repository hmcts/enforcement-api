package uk.gov.hmcts.reform.enforcement.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import uk.gov.service.notify.NotificationClient;

import java.util.concurrent.Executor;

@Configuration
@EnableAsync
public class NotificationsConfiguration {
    @Bean
    public NotificationClient notificationClient(
        @Value("${notify.api-key}") String apiKey
    ) {
        return new NotificationClient(apiKey);
    }

    @Bean
    public Executor notifyTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(5);
        executor.setQueueCapacity(25);
        executor.setThreadNamePrefix("notification-");
        executor.initialize();
        return executor;
    }
}
