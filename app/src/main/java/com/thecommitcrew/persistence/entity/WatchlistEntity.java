package com.thecommitcrew.persistence.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

@Entity
@Table(name = "watchlist")
@IdClass(WatchlistId.class)
public class WatchlistEntity {

    @Id
    @Column(name = "account_id", length = 32)
    private String accountId;

    @Id
    @Column(length = 20)
    private String symbol;

    @Column(name = "added_at", nullable = false)
    private LocalDateTime addedAt;

    public WatchlistEntity() {}

    public WatchlistEntity(String accountId, String symbol, LocalDateTime addedAt) {
        this.accountId = accountId;
        this.symbol = symbol;
        this.addedAt = addedAt;
    }

    public String getAccountId() {
        return accountId;
    }

    public String getSymbol() {
        return symbol;
    }

    public LocalDateTime getAddedAt() {
        return addedAt;
    }
}
