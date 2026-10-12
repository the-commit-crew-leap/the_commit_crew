package com.thecommitcrew.service;

import java.util.List;

import org.springframework.stereotype.Service;

import com.thecommitcrew.domain.dto.InstrumentResponseDTO;
import com.thecommitcrew.domain.exception.InstrumentNotFoundException;
import com.thecommitcrew.persistence.entity.InstrumentEntity;
import com.thecommitcrew.persistence.repository.InstrumentRepository;

@Service
public class InstrumentService {

    private final InstrumentRepository instrumentRepository;

    public InstrumentService(InstrumentRepository instrumentRepository) {
        this.instrumentRepository = instrumentRepository;
    }

    public List<InstrumentResponseDTO> getInstruments(Boolean tradable) {
        List<InstrumentEntity> instruments = tradable == null 
            ? instrumentRepository.findAll()
            : instrumentRepository.findByTradable(tradable);
        return instruments.stream()
            .map(this::toResponse)
            .toList();
    }

    public InstrumentResponseDTO getInstrument(String symbol) {
        return instrumentRepository.findBySymbol(symbol)
            .map(this::toResponse)
            .orElseThrow(() -> new InstrumentNotFoundException("Instrument not found for symbol: " + symbol));
    }

    private InstrumentResponseDTO toResponse(InstrumentEntity instrument) {
        return new InstrumentResponseDTO(
            instrument.getSymbol(),
            instrument.getName(),
            instrument.getAssetClass(),
            instrument.getCurrency(),
            instrument.getTradable()
        );
    }
}