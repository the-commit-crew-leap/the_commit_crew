package com.thecommitcrew.service;

import java.math.BigDecimal;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.thecommitcrew.domain.model.PriceHistory;
import com.thecommitcrew.domain.exception.NegativePriceException;
import com.thecommitcrew.domain.exception.PriceNotFoundException;
import com.thecommitcrew.persistence.mapper.PriceHistoryMapper;

@Service 
public class PriceService {
    private final PriceHistoryMapper priceHistoryMapper;
    private final AlpacaMarketDataClient alpacaMarketDataClient;

    public PriceService(PriceHistoryMapper priceHistoryMapper) {
        this(priceHistoryMapper, null);
    }

    @Autowired
    public PriceService(
        PriceHistoryMapper priceHistoryMapper,
        AlpacaMarketDataClient alpacaMarketDataClient
    ) {
        this.priceHistoryMapper = priceHistoryMapper;
        this.alpacaMarketDataClient = alpacaMarketDataClient;
    }

    public BigDecimal getCurrentPrice(String symbol) {
        if (alpacaMarketDataClient != null && alpacaMarketDataClient.isConfigured()) {
            return validatePrice(symbol, alpacaMarketDataClient.getLatestTradePrice(symbol));
        }

        Optional<PriceHistory> priceHistory = priceHistoryMapper.findLatestPriceBySymbol(symbol);
        
        if (priceHistory.isEmpty()) {
            throw new PriceNotFoundException("No price found for symbol: " + symbol);
        }

        return validatePrice(symbol, priceHistory.get().getClosePrice());
    }

    private BigDecimal validatePrice(String symbol, BigDecimal price) {
        if (price == null || price.compareTo(BigDecimal.ZERO) < 0) {
            throw new NegativePriceException("Close price cannot be negative for symbol: " + symbol);
        }
        return price;
    }
}