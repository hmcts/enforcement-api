package uk.gov.hmcts.reform.enforcement.ccd.domain;

import lombok.Builder;
import lombok.Data;
import uk.gov.hmcts.ccd.sdk.api.CCD;

/**
 * The main domain model representing a possessions case.
 */
@Builder
@Data
public class EnforcementCase {
    @CCD(label = "Applicant's first name")
    private String applicantForename;

    @CCD(label = "Party A")
    private Party partyA;
}
