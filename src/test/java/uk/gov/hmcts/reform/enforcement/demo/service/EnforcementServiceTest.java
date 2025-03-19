package uk.gov.hmcts.reform.enforcement.demo.service;

import org.junit.jupiter.api.Test;
import uk.gov.hmcts.reform.enforcement.demo.model.AccountHolder;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public class EnforcementServiceTest {
    @Test
    void calculateFine() {
        var enforcementService = new EnforcementService();
        assertThat(enforcementService.calculateFine(100,70),is(300.0));
    }

    @Test
    void settleFineForAccountHolder() {
        var accountHolder = AccountHolder.builder()
            .accountName("Jim Bean").accountNumber("54321").balance(1000).penalty(0).build();
        var enforcementService = new EnforcementService();
        var accountHolderWithFine = enforcementService.settleFineForAccountHolder(accountHolder,100,70);
        assertThat(accountHolderWithFine,is(
            AccountHolder.builder().accountName("Jim Bean").accountNumber("54321").balance(700).penalty(300).build()));
    }
}
