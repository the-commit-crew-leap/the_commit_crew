package com.thecommitcrew.service;

import java.math.BigDecimal;
import java.util.Optional;

import org.springframework.stereotype.Component;

import com.thecommitcrew.domain.model.PriceHistory;
import com.thecommitcrew.domain.exception.NegativePriceException;
import com.thecommitcrew.domain.exception.PriceNotFoundException;
import com.thecommitcrew.persistence.mapper.PriceHistoryMapper;

@Component
public class PriceService {
    private final PriceHistoryMapper priceHistoryMapper;

    public PriceService(PriceHistoryMapper priceHistoryMapper) {
        this.priceHistoryMapper = priceHistoryMapper;
    }

    public BigDecimal getCurrentPrice(String symbol) {
        Optional<PriceHistory> priceHistory = priceHistoryMapper.findLatestPriceBySymbol(symbol);
        
        if (priceHistory.isEmpty()) {
            throw new PriceNotFoundException("No price found for symbol: " + symbol);
        }

        BigDecimal closePrice = priceHistory.get().getClosePrice();
        
        if (closePrice.compareTo(BigDecimal.ZERO) < 0) {
            throw new NegativePriceException("Close price cannot be negative for symbol: " + symbol);
        }

        return closePrice;
    }
}