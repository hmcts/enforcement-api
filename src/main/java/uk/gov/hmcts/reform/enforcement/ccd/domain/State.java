package uk.gov.hmcts.reform.enforcement.ccd.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import uk.gov.hmcts.ccd.sdk.api.CCD;

/**
 * All possible Enforcement case states.
 * Converted into CCD states.
 */
@RequiredArgsConstructor
@Getter
public enum State {

    @CCD(
        label = "Open",
        access = {DefaultStateAccess.class}
    )
    Open;
}
