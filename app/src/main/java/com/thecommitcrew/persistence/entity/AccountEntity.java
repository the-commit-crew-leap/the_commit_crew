package com.thecommitcrew.persistence.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.thecommitcrew.domain.enums.AccountStatus;

import jakarta.persistence.*;

@Entity
@Table(name = "accounts")
public class AccountEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "account_id", nullable = false, unique = true, length = 32)
    private String accountId;
    
    @Column(name = "holder_name", nullable = false, length = 255)
    private String holderName;
    
    @Column(name = "cash_balance", nullable = false)
    private BigDecimal cashBalance;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AccountStatus status;
    
    @Column(nullable = false)
    @Version
    private int version;
    
    @Column(name = "last_updated", nullable = false)
    private LocalDateTime lastUpdated;
    
    // Parameterless constructor for JPA
    public AccountEntity() {}
    
    public AccountEntity(String accountId, String holderName, BigDecimal cashBalance,
                        AccountStatus status, int version, LocalDateTime lastUpdated) {
        this.accountId = accountId;
        this.holderName = holderName;
        this.cashBalance = cashBalance;
        this.status = status;
        this.version = version;
        this.lastUpdated = lastUpdated;
    }
    
    // Getters and setters
    public Long getId() {
        return id;
    }
    
    // temporary, delete after changing id to uuid
    public void setId(Long id) {
        this.id = id;
    }
    public String getAccountId() {
        return accountId;
    }
    public void setAccountId(String accountId) {
        this.accountId = accountId;
    }
    
    public String getHolderName() {
        return holderName;
    }
    public void setHolderName(String holderName) {
        this.holderName = holderName;
    }
    
    public java.math.BigDecimal getCashBalance() {
        return cashBalance;
    }
    public void setCashBalance(java.math.BigDecimal cashBalance) {
        this.cashBalance = cashBalance;
    }
    
    public AccountStatus getStatus() {
        return status;
    }
    public void setStatus(AccountStatus status) {
        this.status = status;
    }
    
    public int getVersion() {
        return version;
    }
    public void setVersion(int version) {
        this.version = version;
    }
    
    public LocalDateTime getLastUpdated() {
        return lastUpdated;
    }
    public void setLastUpdated(LocalDateTime lastUpdated) {
        this.lastUpdated = lastUpdated;
    }

}
