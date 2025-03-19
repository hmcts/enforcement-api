package uk.gov.hmcts.reform.enforcement.demo.service;

import org.springframework.stereotype.Service;
import uk.gov.hmcts.reform.enforcement.demo.model.AccountHolder;

@Service
public class EnforcementService {

    public double calculateFine(double speed, double limit) {
        return speed > limit ? (speed - limit) * 10 : 0;
    }

    public AccountHolder settleFineForAccountHolder(AccountHolder accountHolder, double speed, double limit) {
        var fine = calculateFine(speed, limit);
        var balance = accountHolder.getBalance() - fine;
        var penalty = accountHolder.getPenalty() + fine;

        return AccountHolder.builder()
            .accountName(accountHolder.getAccountName())
            .accountNumber(accountHolder.getAccountNumber())
            .balance(balance)
            .penalty(penalty)
            .build();
    }
}
