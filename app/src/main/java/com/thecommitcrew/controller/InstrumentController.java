package com.thecommitcrew.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.thecommitcrew.domain.dto.InstrumentResponseDTO;
import com.thecommitcrew.domain.dto.LivePriceResponse;
import com.thecommitcrew.service.InstrumentService;
import com.thecommitcrew.service.PriceService;

@RestController
@RequestMapping("/api/v1/instruments")
public class InstrumentController {

	private final InstrumentService instrumentService;
	private final PriceService priceService;

	public InstrumentController(InstrumentService instrumentService, PriceService priceService) {
		this.instrumentService = instrumentService;
		this.priceService = priceService;
	}

	@GetMapping
	public ResponseEntity<List<InstrumentResponseDTO>> getInstruments(
		@RequestParam(required = false) Boolean tradable) {
	return ResponseEntity.ok(instrumentService.getInstruments(tradable));
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
}