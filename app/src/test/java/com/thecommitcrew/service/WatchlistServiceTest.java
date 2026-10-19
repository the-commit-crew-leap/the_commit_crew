package com.thecommitcrew.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.thecommitcrew.domain.enums.AssetClass;
import com.thecommitcrew.persistence.entity.InstrumentEntity;
import com.thecommitcrew.persistence.entity.WatchlistEntity;
import com.thecommitcrew.persistence.repository.InstrumentRepository;
import com.thecommitcrew.persistence.repository.WatchlistRepository;

@ExtendWith(MockitoExtension.class)
class WatchlistServiceTest {

    @Mock
    private WatchlistRepository watchlistRepository;

    @Mock
    private InstrumentRepository instrumentRepository;

    @InjectMocks
    private WatchlistService watchlistService;

    @Test
    void addsAnExistingInstrumentOnlyOnce() {
        InstrumentEntity instrument = new InstrumentEntity(
            "AAPL", "Apple Inc.", AssetClass.EQUITY, "USD", true);
        when(instrumentRepository.findBySymbol("AAPL")).thenReturn(Optional.of(instrument));
        when(watchlistRepository.findByAccountIdAndSymbol("ACC-1001", "AAPL"))
            .thenReturn(Optional.of(new WatchlistEntity("ACC-1001", "AAPL", LocalDateTime.now())));

        var response = watchlistService.addToWatchlist("ACC-1001", " aapl ");

        assertEquals("AAPL", response.symbol());
        verify(watchlistRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void removesByAccountAndSymbol() {
        watchlistService.removeFromWatchlist("ACC-1001", " aapl ");

        verify(watchlistRepository).deleteByAccountIdAndSymbol("ACC-1001", "AAPL");
    }
}
