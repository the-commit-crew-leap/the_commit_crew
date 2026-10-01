package com.thecommitcrew.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.thecommitcrew.domain.dto.InstrumentResponseDTO;
import com.thecommitcrew.service.InstrumentService;

@RestController
@RequestMapping("/instruments")
public class InstrumentController {

	private final InstrumentService instrumentService;

	public InstrumentController(InstrumentService instrumentService) {
		this.instrumentService = instrumentService;
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
}
