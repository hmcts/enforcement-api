package uk.gov.hmcts.reform.enforcement.notify.task;

import com.github.kagkarlsson.scheduler.task.CompletionHandler;
import com.github.kagkarlsson.scheduler.task.TaskDescriptor;
import com.github.kagkarlsson.scheduler.task.helper.CustomTask;
import com.github.kagkarlsson.scheduler.task.helper.Tasks;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.reform.enforcement.notify.model.EmailState;

@Component
@Slf4j
public class SendEmailTaskComponent {
    private static final String SEND_EMAIL_TASK_NAME = "send-email-task";

    public static final TaskDescriptor<EmailState> sendEmailTask =
        TaskDescriptor.of(SEND_EMAIL_TASK_NAME, EmailState.class);

    // This is a dummy task made for future work
    @Bean
    public static CustomTask<EmailState> sendEmailTask() {
        return Tasks.custom(sendEmailTask)
            .execute((taskInstance, executionContext) -> {
                log.info("This is a placeholder for sending email task");
                return new CompletionHandler.OnCompleteRemove<>();
            });
    }
}
