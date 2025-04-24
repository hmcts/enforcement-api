package uk.gov.hmcts.reform.enforcement.ccd;

import org.springframework.stereotype.Component;
import uk.gov.hmcts.ccd.sdk.api.CCDConfig;
import uk.gov.hmcts.ccd.sdk.api.ConfigBuilder;
import uk.gov.hmcts.reform.enforcement.ccd.domain.EnforcementCase;
import uk.gov.hmcts.reform.enforcement.ccd.domain.State;
import uk.gov.hmcts.reform.enforcement.ccd.domain.UserRole;

/**
 * Setup some common possessions case type configuration.
 */
@Component
public class CaseType implements CCDConfig<EnforcementCase, State, UserRole> {

    @Override
    public void configure(final ConfigBuilder<EnforcementCase, State, UserRole> builder) {
        builder.setCallbackHost("http://localhost:3206");

        builder.caseType("PCS", "Civil Possessions", "Possessions");
        builder.jurisdiction("CIVIL", "Civil Possessions", "The new one");

        var label = "Applicant Forename";
        builder.searchInputFields()
            .field(EnforcementCase::getApplicantForename, label);
        builder.searchCasesFields()
            .field(EnforcementCase::getApplicantForename, label);

        builder.searchResultFields()
            .field(EnforcementCase::getApplicantForename, label);
        builder.workBasketInputFields()
            .field(EnforcementCase::getApplicantForename, label);
        builder.workBasketResultFields()
            .field(EnforcementCase::getApplicantForename, label);

        builder.tab("Example", "Example Tab")
            .field(EnforcementCase::getApplicantForename)
            .field(EnforcementCase::getPartyA);
    }
}
