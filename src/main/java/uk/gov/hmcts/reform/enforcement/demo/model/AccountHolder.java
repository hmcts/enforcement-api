package uk.gov.hmcts.reform.enforcement.demo.model;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AccountHolder {

    private final String accountName;
    private final String accountNumber;
    private final double balance;
    private final double penalty;
}
