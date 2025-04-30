package uk.gov.hmcts.reform.enforcement.ccd;

import uk.gov.hmcts.ccd.sdk.api.CCDConfig;
import uk.gov.hmcts.ccd.sdk.api.ConfigBuilder;
import uk.gov.hmcts.reform.enforcement.ccd.domain.EnforcementCase;
import uk.gov.hmcts.reform.enforcement.ccd.domain.State;
import uk.gov.hmcts.reform.enforcement.ccd.domain.UserRole;

import static java.lang.System.getenv;
import static java.util.Optional.ofNullable;

public class CaseType implements CCDConfig<EnforcementCase, State, UserRole> {

    private static final String CASE_TYPE_ID = "Enforcement";
    private static final String CASE_TYPE_NAME = "Civil Possessions";
    private static final String CASE_TYPE_DESCRIPTION = "Civil Possessions Case Type";
    private static final String JURISDICTION_ID = "CIVIL";
    private static final String JURISDICTION_NAME = "Civil Possessions";
    private static final String JURISDICTION_DESCRIPTION = "Civil Possessions Jurisdiction";

    public static String getCaseType() {
        return withChangeId(CASE_TYPE_ID, "-");
    }

    public static String getCaseTypeName() {
        return withChangeId(CASE_TYPE_NAME, " ");
    }

    private static String withChangeId(String base, String separator) {
        return ofNullable(getenv().get("CHANGE_ID"))
            .map(changeId -> base + separator + changeId)
            .orElse(base);
    }

    @Override
    public void configure(final ConfigBuilder<EnforcementCase, State, UserRole> builder) {
        builder.setCallbackHost(getenv().getOrDefault("CASE_API_URL", "http://localhost:3206"));

        builder.caseType(getCaseType(), getCaseTypeName(), CASE_TYPE_DESCRIPTION);
        builder.jurisdiction(JURISDICTION_ID, JURISDICTION_NAME, JURISDICTION_DESCRIPTION);

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
