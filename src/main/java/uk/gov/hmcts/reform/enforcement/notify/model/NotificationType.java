package uk.gov.hmcts.reform.enforcement.notify.model;

public enum NotificationType {
    EMAIL,
    TEXT_MESSAGE,
    LETTER;

    @Override
    public String toString() {
        return name();
    }
}
