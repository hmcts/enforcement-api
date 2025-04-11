package uk.gov.hmcts.reform.enforcement.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SectionResponse {
    private String groupId;
    private String status;
    private Task tasks;
}
