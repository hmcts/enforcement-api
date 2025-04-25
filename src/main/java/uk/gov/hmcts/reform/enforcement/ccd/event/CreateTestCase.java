package uk.gov.hmcts.reform.enforcement.ccd.event;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.ccd.sdk.api.CCDConfig;
import uk.gov.hmcts.ccd.sdk.api.CaseDetails;
import uk.gov.hmcts.ccd.sdk.api.ConfigBuilder;
import uk.gov.hmcts.ccd.sdk.api.Permission;
import uk.gov.hmcts.ccd.sdk.api.callback.AboutToStartOrSubmitResponse;
import uk.gov.hmcts.reform.enforcement.ccd.domain.EnforcementCase;
import uk.gov.hmcts.reform.enforcement.ccd.domain.State;
import uk.gov.hmcts.reform.enforcement.ccd.domain.UserRole;

@Profile("dev") // Non-prod event
@Component
public class CreateTestCase implements CCDConfig<EnforcementCase, State, UserRole> {
    @Override
    public void configure(ConfigBuilder<EnforcementCase, State, UserRole> configBuilder) {
        configBuilder
            .event("createTestApplication")
            .initialState(State.Open)
            .name("Create test case")
            .aboutToStartCallback(this::start)
            .aboutToSubmitCallback(this::aboutToSubmit)
            .grant(Permission.CRUD, UserRole.CASE_WORKER)
            .fields()
            .page("Create test case")
            .mandatory(EnforcementCase::getApplicantForename)
            .done();
    }

    private AboutToStartOrSubmitResponse<EnforcementCase, State> start(
        CaseDetails<EnforcementCase, State> caseDetails) {
        EnforcementCase data = caseDetails.getData();
        data.setApplicantForename("Preset value");

        return AboutToStartOrSubmitResponse.<EnforcementCase, State>builder()
            .data(caseDetails.getData())
            .build();
    }

    public AboutToStartOrSubmitResponse<EnforcementCase, State> aboutToSubmit(
        CaseDetails<EnforcementCase, State> details,
        CaseDetails<EnforcementCase, State> beforeDetails) {
        // TODO: Whatever you need.
        return AboutToStartOrSubmitResponse.<EnforcementCase, State>builder()
            .data(details.getData())
            .build();
    }
}
