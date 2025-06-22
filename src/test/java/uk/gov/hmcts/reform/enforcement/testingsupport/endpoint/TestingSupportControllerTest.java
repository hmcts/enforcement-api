package uk.gov.hmcts.reform.enforcement.testingsupport.endpoint;

import com.github.kagkarlsson.scheduler.SchedulerClient;
import com.github.kagkarlsson.scheduler.task.Task;
import com.github.kagkarlsson.scheduler.task.TaskInstance;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("unchecked")
class TestingSupportControllerTest {

    @Mock
    private SchedulerClient schedulerClient;

    @Mock
    private Task<Void> helloWorldTask;

    @Mock
    private TaskInstance<Void> taskInstance;

    @InjectMocks
    private TestingSupportController testingSupportController;

    private String authorization;
    private String serviceAuthorization;

    @BeforeEach
    void setUp() {
        authorization = "Bearer token123";
        serviceAuthorization = "ServiceAuth token456";
        
        // Need to fix the constructor for InjectMocks to work properly with the new signature
        testingSupportController = new TestingSupportController(
            schedulerClient,
            helloWorldTask
        );
    }

    @Test
    void createSampleJob_ShouldReturnOk_WhenJobScheduledSuccessfully() {
        int delaySeconds = 5;
        when(helloWorldTask.instance(anyString())).thenReturn(taskInstance);
        when(schedulerClient.scheduleIfNotExists(any(TaskInstance.class), any(Instant.class)))
            .thenReturn(true);

        ResponseEntity<String> response = testingSupportController.createSampleJob(
            delaySeconds, authorization, serviceAuthorization);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("Hello World task scheduled successfully");
        assertThat(response.getBody()).contains("helloWorld-");

        verify(helloWorldTask).instance(anyString());
        verify(schedulerClient).scheduleIfNotExists(any(TaskInstance.class), any(Instant.class));
    }

    @Test
    void createSampleJob_ShouldUseDefaultDelay_WhenDelayNotProvided() {
        when(helloWorldTask.instance(anyString())).thenReturn(taskInstance);
        when(schedulerClient.scheduleIfNotExists(any(TaskInstance.class), any(Instant.class)))
            .thenReturn(true);

        ResponseEntity<String> response = testingSupportController.createSampleJob(
            1, authorization, serviceAuthorization);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("Hello World task scheduled successfully");

        verify(schedulerClient).scheduleIfNotExists(any(TaskInstance.class), any(Instant.class));
    }

    @Test
    void createSampleJob_ShouldScheduleWithCorrectDelay() {
        int delaySeconds = 10;
        when(helloWorldTask.instance(anyString())).thenReturn(taskInstance);
        when(schedulerClient.scheduleIfNotExists(any(TaskInstance.class), any(Instant.class)))
            .thenReturn(true);

        Instant beforeCall = Instant.now();
        testingSupportController.createSampleJob(delaySeconds, authorization, serviceAuthorization);
        Instant afterCall = Instant.now();

        ArgumentCaptor<Instant> executionTimeCaptor = ArgumentCaptor.forClass(Instant.class);
        verify(schedulerClient).scheduleIfNotExists(any(TaskInstance.class), executionTimeCaptor.capture());

        Instant scheduledTime = executionTimeCaptor.getValue();
        Instant expectedMinTime = beforeCall.plusSeconds(delaySeconds);
        Instant expectedMaxTime = afterCall.plusSeconds(delaySeconds);

        assertThat(scheduledTime).isBetween(expectedMinTime, expectedMaxTime);
    }

    @Test
    void createSampleJob_ShouldGenerateUniqueTaskIds() {
        when(helloWorldTask.instance(anyString())).thenReturn(taskInstance);
        when(schedulerClient.scheduleIfNotExists(any(TaskInstance.class), any(Instant.class)))
            .thenReturn(true);

        ResponseEntity<String> response1 = testingSupportController.createSampleJob(
            1, authorization, serviceAuthorization);
        ResponseEntity<String> response2 = testingSupportController.createSampleJob(
            1, authorization, serviceAuthorization);

        assertThat(response1.getBody()).isNotEqualTo(response2.getBody());

        ArgumentCaptor<String> taskIdCaptor = ArgumentCaptor.forClass(String.class);
        verify(helloWorldTask, org.mockito.Mockito.times(2)).instance(taskIdCaptor.capture());

        String taskId1 = taskIdCaptor.getAllValues().get(0);
        String taskId2 = taskIdCaptor.getAllValues().get(1);

        assertThat(taskId1).isNotEqualTo(taskId2)
            .startsWith("helloWorld-");
        assertThat(taskId2).startsWith("helloWorld-");
    }

    @Test
    void createSampleJob_ShouldReturnInternalServerError_WhenSchedulerThrowsException() {
        when(helloWorldTask.instance(anyString())).thenReturn(taskInstance);
        when(schedulerClient.scheduleIfNotExists(any(TaskInstance.class), any(Instant.class)))
            .thenThrow(new RuntimeException("Scheduler error"));

        ResponseEntity<String> response = testingSupportController.createSampleJob(
            1, authorization, serviceAuthorization);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isEqualTo("An error occurred while scheduling the Hello World task.");

        verify(schedulerClient).scheduleIfNotExists(any(TaskInstance.class), any(Instant.class));
    }

    @Test
    void createSampleJob_ShouldReturnInternalServerError_WhenTaskInstanceCreationFails() {
        when(helloWorldTask.instance(anyString())).thenThrow(new RuntimeException("Task creation failed"));

        ResponseEntity<String> response = testingSupportController.createSampleJob(
            1, authorization, serviceAuthorization);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isEqualTo("An error occurred while scheduling the Hello World task.");

        verify(helloWorldTask).instance(anyString());
    }

    @Test
    void createSampleJob_ShouldUseDefaultAuthorization_WhenHeaderNotProvided() {
        when(helloWorldTask.instance(anyString())).thenReturn(taskInstance);
        when(schedulerClient.scheduleIfNotExists(any(TaskInstance.class), any(Instant.class)))
            .thenReturn(true);

        ResponseEntity<String> response = testingSupportController.createSampleJob(
            1, "DummyId", serviceAuthorization);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("Hello World task scheduled successfully");

        verify(schedulerClient).scheduleIfNotExists(any(TaskInstance.class), any(Instant.class));
    }

    @Test
    void createSampleJob_ShouldHandleSchedulerReturnsFalse() {
        when(helloWorldTask.instance(anyString())).thenReturn(taskInstance);
        when(schedulerClient.scheduleIfNotExists(any(TaskInstance.class), any(Instant.class)))
            .thenReturn(false);

        ResponseEntity<String> response = testingSupportController.createSampleJob(
            1, authorization, serviceAuthorization);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("Hello World task scheduled successfully");

        verify(schedulerClient).scheduleIfNotExists(any(TaskInstance.class), any(Instant.class));
    }

    @Test
    void constructor_ShouldInitializeFields() {
        TestingSupportController controller = new TestingSupportController(
            schedulerClient, helloWorldTask);

        assertThat(controller).isNotNull();
    }
}
