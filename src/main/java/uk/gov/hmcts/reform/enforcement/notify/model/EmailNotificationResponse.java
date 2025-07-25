package uk.gov.hmcts.reform.enforcement.notify.model;

import lombok.Data;

import java.util.UUID;

@Data
public class EmailNotificationResponse {
    private String taskId;
    private String status;
    private UUID notificationId; // Database notification record ID
}
