package com.thecommitcrew.service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.thecommitcrew.domain.dto.InstrumentResponseDTO;
import com.thecommitcrew.domain.exception.InstrumentNotFoundException;
import com.thecommitcrew.persistence.entity.InstrumentEntity;
import com.thecommitcrew.persistence.entity.WatchlistEntity;
import com.thecommitcrew.persistence.repository.InstrumentRepository;
import com.thecommitcrew.persistence.repository.WatchlistRepository;

@Service
public class WatchlistService {

    private final WatchlistRepository watchlistRepository;
    private final InstrumentRepository instrumentRepository;

    public WatchlistService(
        WatchlistRepository watchlistRepository,
        InstrumentRepository instrumentRepository
    ) {
        this.watchlistRepository = watchlistRepository;
        this.instrumentRepository = instrumentRepository;
    }

    @Transactional(readOnly = true)
    public List<InstrumentResponseDTO> getWatchlist(String accountId) {
        return watchlistRepository.findByAccountIdOrderByAddedAtAsc(accountId).stream()
            .map(entry -> toResponse(instrumentRepository.findBySymbol(entry.getSymbol())
                .orElseThrow(() -> new InstrumentNotFoundException(
                    "Instrument not found for symbol: " + entry.getSymbol()))))
            .toList();
    }

    @Transactional
    public InstrumentResponseDTO addToWatchlist(String accountId, String symbol) {
        String normalizedSymbol = symbol.trim().toUpperCase();
        InstrumentEntity instrument = instrumentRepository.findBySymbol(normalizedSymbol)
            .orElseThrow(() -> new InstrumentNotFoundException(
                "Instrument not found for symbol: " + normalizedSymbol));

        if (watchlistRepository.findByAccountIdAndSymbol(accountId, normalizedSymbol).isEmpty()) {
            watchlistRepository.save(new WatchlistEntity(accountId, normalizedSymbol, LocalDateTime.now()));
        }

        return toResponse(instrument);
    }

    @Transactional
    public void removeFromWatchlist(String accountId, String symbol) {
        watchlistRepository.deleteByAccountIdAndSymbol(accountId, symbol.trim().toUpperCase());
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
