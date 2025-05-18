package uk.gov.hmcts.reform.enforcement.notify.model;

public enum NotificationStatus {
    CREATED("created"),
    SENDING("sending"),
    DELIVERED("delivered"),
    PERMANENT_FAILURE("permanent-failure"),
    TEMPORARY_FAILURE("temporary-failure"),
    TECHNICAL_FAILURE("technical-failure"),
    SCHEDULE("scheduled"),
    PENDING_SCHEDULE("pending-schedule"), 
    SUBMITTED("submitted");

    private final String apiValue;

    NotificationStatus(String apiValue) {
        this.apiValue = apiValue;
    }

    public String getApiValue() {
        return apiValue;
    }

    @Override
    public String toString() {
        return apiValue;
    }

    public static NotificationStatus fromString(String status) {
        for (NotificationStatus notificationStatus : NotificationStatus.values()) {
            if (notificationStatus.apiValue.equalsIgnoreCase(status)) {
                return notificationStatus;
            }
        }
        throw new IllegalArgumentException("Unknown status: " + status);
    }
}
