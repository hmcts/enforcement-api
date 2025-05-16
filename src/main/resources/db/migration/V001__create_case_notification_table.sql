CREATE TABLE case_notification (
    notification_id UUID PRIMARY KEY,
    case_id UUID NOT NULL,
    provider_notification_id UUID,
    submitted_at TIMESTAMP,
    scheduled_at TIMESTAMP,
    last_updated_at TIMESTAMP NOT NULL,
    status VARCHAR(50) NOT NULL,
    type VARCHAR(50) NOT NULL,
    recipient VARCHAR(255) NOT NULL
);

CREATE INDEX idx_case_notification_case_id ON case_notification(case_id);
CREATE INDEX idx_case_notification_provider_id ON case_notification(provider_notification_id);
