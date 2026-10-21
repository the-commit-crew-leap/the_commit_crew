package com.thecommitcrew.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.thecommitcrew.domain.model.PriceHistory;
import com.thecommitcrew.domain.dto.LivePriceResponse;
import com.thecommitcrew.domain.dto.IntradayPriceResponse;
import com.thecommitcrew.domain.exception.NegativePriceException;
import com.thecommitcrew.domain.exception.PriceNotFoundException;
import com.thecommitcrew.domain.exception.LivePriceUnavailableException;
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
            return validatePrice(symbol, alpacaMarketDataClient.getLatestTrade(symbol).price());
        }

        Optional<PriceHistory> priceHistory = priceHistoryMapper.findLatestPriceBySymbol(symbol);
        
        if (priceHistory.isEmpty()) {
            throw new PriceNotFoundException("No price found for symbol: " + symbol);
        }

        return validatePrice(symbol, priceHistory.get().getClosePrice());
    }

    public LivePriceResponse getCurrentLivePrice(String symbol) {
        if (alpacaMarketDataClient == null || !alpacaMarketDataClient.isConfigured()) {
            throw new LivePriceUnavailableException(
                "Live Alpaca market data is not configured for symbol: " + symbol
            );
        }

        try {
            AlpacaMarketDataClient.LatestTrade trade = alpacaMarketDataClient.getLatestTrade(symbol);
            BigDecimal price = validatePrice(symbol, trade.price());
            Instant updatedAt = trade.timestamp() == null ? Instant.now() : trade.timestamp();
            return new LivePriceResponse(
                symbol,
                price,
                "ALPACA_" + alpacaMarketDataClient.feed().toUpperCase(),
                true,
                updatedAt
            );
        } catch (LivePriceUnavailableException | NegativePriceException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new LivePriceUnavailableException(
                "Live price is unavailable for symbol: " + symbol,
                exception
            );
        }
    }

    public IntradayPriceResponse getIntradayPrices(String symbol) {
        if (alpacaMarketDataClient == null || !alpacaMarketDataClient.isConfigured()) {
            throw new LivePriceUnavailableException(
                "Live Alpaca market data is not configured for symbol: " + symbol
            );
        }
        try {
            return alpacaMarketDataClient.getIntradayPrices(symbol);
        } catch (RuntimeException exception) {
            throw new LivePriceUnavailableException(
                "Intraday price data is unavailable for symbol: " + symbol,
                exception
            );
        }
    }

    public LivePriceResponse getLastAvailableClose(String symbol) {
        Optional<PriceHistory> priceHistory = priceHistoryMapper.findLatestPriceBySymbol(symbol);
        if (priceHistory.isEmpty()) {
            throw new LivePriceUnavailableException(
                "No last available session price for symbol: " + symbol
            );
        }

        PriceHistory history = priceHistory.get();
        return new LivePriceResponse(
            symbol,
            validatePrice(symbol, history.getClosePrice()),
            "DATABASE_LAST_CLOSE",
            false,
            history.getPriceDate().atStartOfDay(ZoneOffset.UTC).toInstant()
        );
    }

    private BigDecimal validatePrice(String symbol, BigDecimal price) {
        if (price == null || price.compareTo(BigDecimal.ZERO) < 0) {
            throw new NegativePriceException("Close price cannot be negative for symbol: " + symbol);
        }
        return price;
    }
}