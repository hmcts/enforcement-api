package uk.gov.hmcts.reform.enforcement.demo.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import uk.gov.hmcts.reform.enforcement.demo.model.AccountHolder;

import static org.assertj.core.api.Assertions.assertThat;


public class EnforcementServiceTest {
    @DisplayName("should calculate fine")
    @Test
    void calculateFine() {
        var enforcementService = new EnforcementService();
        assertThat(enforcementService.calculateFine(100,70)).isEqualTo(300.0);
    }

    @DisplayName("should settle fine for account holder")
    @Test
    void settleFineForAccountHolder() {
        var accountHolder = AccountHolder.builder()
            .accountName("Jim Bean").accountNumber("54321").balance(1000).penalty(0).build();
        var enforcementService = new EnforcementService();
        var accountHolderWithFine = enforcementService.settleFineForAccountHolder(accountHolder,100,70);
        assertThat(accountHolderWithFine).isEqualTo(
            AccountHolder.builder().accountName("Jim Bean").accountNumber("54321").balance(700).penalty(300).build());
    }
}
