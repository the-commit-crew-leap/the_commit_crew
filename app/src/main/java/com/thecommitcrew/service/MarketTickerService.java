package com.thecommitcrew.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.thecommitcrew.domain.dto.MarketTickerResponse;

@Service
public class MarketTickerService {
    public static final List<String> DEFAULT_SYMBOLS = List.of("BTC/USD", "ETH/USD", "SOL/USD", "AVAX/USD");

    private static final Logger logger = LoggerFactory.getLogger(MarketTickerService.class);
    private final AlpacaMarketDataClient alpacaMarketDataClient;

    public MarketTickerService(AlpacaMarketDataClient alpacaMarketDataClient) {
        this.alpacaMarketDataClient = alpacaMarketDataClient;
    }

    public List<MarketTickerResponse> getDefaultMarkets() {
        return DEFAULT_SYMBOLS.stream().map(this::getMarket).toList();
    }

    private MarketTickerResponse getMarket(String symbol) {
        if (!alpacaMarketDataClient.isConfigured()) {
            return unavailable(symbol);
        }

        try {
            AlpacaMarketDataClient.CryptoSnapshot snapshot =
                alpacaMarketDataClient.getLatestCryptoSnapshot(symbol);
            BigDecimal changePercent = snapshot.previousClose() == null
                ? null
                : snapshot.price()
                    .subtract(snapshot.previousClose())
                    .divide(snapshot.previousClose(), 8, java.math.RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100));

            return new MarketTickerResponse(
                symbol,
                snapshot.price(),
                changePercent,
                "ALPACA_CRYPTO",
                true,
                snapshot.timestamp() == null ? Instant.now() : snapshot.timestamp()
            );
        } catch (RuntimeException exception) {
            logger.warn("Live crypto price is unavailable for {}", symbol, exception);
            return unavailable(symbol);
        }
    }

    private MarketTickerResponse unavailable(String symbol) {
        return new MarketTickerResponse(symbol, null, null, null, false, null);
    }
}
