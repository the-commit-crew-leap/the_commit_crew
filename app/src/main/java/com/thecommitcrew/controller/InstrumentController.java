package com.thecommitcrew.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.thecommitcrew.domain.dto.InstrumentResponseDTO;
import com.thecommitcrew.domain.dto.FundamentalsResponse;
import com.thecommitcrew.domain.dto.IntradayPriceResponse;
import com.thecommitcrew.domain.dto.LivePriceResponse;
import com.thecommitcrew.domain.dto.MarketTickerResponse;
import com.thecommitcrew.service.InstrumentService;
import com.thecommitcrew.service.MarketTickerService;
import com.thecommitcrew.service.PriceService;
import com.thecommitcrew.service.SecCompanyFactsClient;

@RestController
@RequestMapping("/api/v1/instruments")
public class InstrumentController {

	private final InstrumentService instrumentService;
	private final PriceService priceService;
	private final MarketTickerService marketTickerService;
	private final SecCompanyFactsClient secCompanyFactsClient;

	public InstrumentController(
		InstrumentService instrumentService,
		PriceService priceService,
		MarketTickerService marketTickerService,
		SecCompanyFactsClient secCompanyFactsClient
	) {
		this.instrumentService = instrumentService;
		this.priceService = priceService;
		this.marketTickerService = marketTickerService;
		this.secCompanyFactsClient = secCompanyFactsClient;
	}

	@GetMapping
	public ResponseEntity<List<InstrumentResponseDTO>> getInstruments(
		@RequestParam(required = false) Boolean tradable) {
	return ResponseEntity.ok(instrumentService.getInstruments(tradable));
	}

	@GetMapping("/markets")
	public ResponseEntity<List<MarketTickerResponse>> getMarketTicker() {
		return ResponseEntity.ok(marketTickerService.getDefaultMarkets());
	}

	@GetMapping("/{symbol}")
	public ResponseEntity<InstrumentResponseDTO> getInstrument(@PathVariable String symbol) {
		return ResponseEntity.ok(instrumentService.getInstrument(symbol));
	}

	@GetMapping("/{symbol}/price")
	public ResponseEntity<LivePriceResponse> getCurrentPrice(@PathVariable String symbol) {
		String normalizedSymbol = symbol.trim().toUpperCase();
		return ResponseEntity.ok(priceService.getCurrentLivePrice(normalizedSymbol));
	}

	@GetMapping("/{symbol}/intraday")
	public ResponseEntity<IntradayPriceResponse> getIntradayPrices(@PathVariable String symbol) {
		String normalizedSymbol = symbol.trim().toUpperCase();
		return ResponseEntity.ok(priceService.getIntradayPrices(normalizedSymbol));
	}

	@GetMapping("/{symbol}/fundamentals")
	public ResponseEntity<FundamentalsResponse> getFundamentals(@PathVariable String symbol) {
		String normalizedSymbol = symbol.trim().toUpperCase();
		return ResponseEntity.ok(secCompanyFactsClient.getFundamentals(normalizedSymbol));
	}
}