package com.thecommitcrew.domain.model;

import java.time.LocalDateTime;
import java.util.Objects;

import com.thecommitcrew.domain.enums.AccountStatus;
import com.thecommitcrew.domain.validator.AccountStatusValidator;

/**
 * Immutable aggregate root representing a trading account.
 * Maintains account state and enforces business rules.
 */
public class Account {
    private final Long accountId;
    private final String holderName;
    private final Money cashBalance;
    private final AccountStatus status;
    private final int version;
    private final LocalDateTime lastUpdated;
    
    private final AccountStatusValidator statusValidator;

    // Constructor
    public Account(Long accountId, String holderName, Money cashBalance, 
                   AccountStatus status, int version, LocalDateTime lastUpdated,
                   AccountStatusValidator statusValidator) {
        this.accountId = validateNotNull(accountId, "Account ID cannot be null");
        this.holderName = validateNotBlank(holderName, "Holder name cannot be blank");
        this.cashBalance = validateNotNull(cashBalance, "Cash balance cannot be null");
        this.status = validateNotNull(status, "Status cannot be null");
        this.version = validateNotNull(version, "Version cannot be null");
        this.lastUpdated = validateNotNull(lastUpdated, "Last updated cannot be null");
        this.statusValidator = validateNotNull(statusValidator, "Status validator cannot be null");
    }

    // Methods

    // Returns a new Account with updated balance (immutable pattern)
    public Account debit(Money amount) {
        statusValidator.validateCanDebit(this.status);
        Money newBalance = this.cashBalance.subtract(amount);
        return new Account(accountId, holderName, newBalance, status, version + 1, 
                          LocalDateTime.now(), statusValidator);  
    }

    public Account credit(Money amount) {
        statusValidator.validateCanCredit(this.status);
        Money newBalance = this.cashBalance.add(amount);
        return new Account(accountId, holderName, newBalance, status, version + 1, 
                          LocalDateTime.now(), statusValidator);
    }

    public Account updateStatus(AccountStatus newStatus) {
        return new Account(accountId, holderName, cashBalance, newStatus, version + 1, 
                          LocalDateTime.now(), statusValidator);
    }

    // Getters only (no setters)
    public Long getAccountId() { return accountId; }
    public String getHolderName() { return holderName; }
    public Money getCashBalance() { return cashBalance; }
    public AccountStatus getStatus() { return status; }
    public int getVersion() { return version; }
    public boolean isActive() { return status == AccountStatus.ACTIVE; }
    public LocalDateTime getLastUpdated() { return lastUpdated; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Account account = (Account) o;
        return Objects.equals(accountId, account.accountId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(accountId);
    }

    @Override
    public String toString() {
        return String.format("Account{id=%d, holder=%s, balance=%s, status=%s}", 
            accountId, holderName, cashBalance, status);
    }

    private static <T> T validateNotNull(T value, String message) {
        if (value == null) throw new IllegalArgumentException(message);
        return value;
    }

    private static String validateNotBlank(String value, String message) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(message);
        return value;
    }
}