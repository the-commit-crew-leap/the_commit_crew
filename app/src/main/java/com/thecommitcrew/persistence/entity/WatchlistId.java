package com.thecommitcrew.persistence.entity;

import java.io.Serializable;
import java.util.Objects;

public class WatchlistId implements Serializable {
    private String accountId;
    private String symbol;

    public WatchlistId() {}

    public WatchlistId(String accountId, String symbol) {
        this.accountId = accountId;
        this.symbol = symbol;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (!(object instanceof WatchlistId other)) {
            return false;
        }
        return Objects.equals(accountId, other.accountId)
            && Objects.equals(symbol, other.symbol);
    }

    @Override
    public int hashCode() {
        return Objects.hash(accountId, symbol);
    }
}
