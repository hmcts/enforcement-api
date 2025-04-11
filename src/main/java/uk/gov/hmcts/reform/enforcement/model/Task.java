package uk.gov.hmcts.reform.enforcement.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Task {
    private String templateId;
    private Map<String, Object> templateValues;
}
