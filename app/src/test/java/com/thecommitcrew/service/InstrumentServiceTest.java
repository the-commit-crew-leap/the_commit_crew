package com.thecommitcrew.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.thecommitcrew.domain.dto.InstrumentResponseDTO;
import com.thecommitcrew.domain.enums.AssetClass;
import com.thecommitcrew.domain.exception.InstrumentNotFoundException;
import com.thecommitcrew.persistence.entity.InstrumentEntity;
import com.thecommitcrew.persistence.repository.InstrumentRepository;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("null")
class InstrumentServiceTest {

    @Mock
    private InstrumentRepository instrumentRepository;

    private InstrumentService instrumentService;

    private InstrumentEntity tradableInstrument;
    private InstrumentEntity untradableInstrument;

    @BeforeEach
    void setUp() {
        instrumentService = new InstrumentService(instrumentRepository);

        tradableInstrument = new InstrumentEntity();
        tradableInstrument.setSymbol("AAPL");
        tradableInstrument.setName("Apple Inc.");
        tradableInstrument.setAssetClass(AssetClass.EQUITY);
        tradableInstrument.setCurrency("USD");
        tradableInstrument.setTradable(true);

        untradableInstrument = new InstrumentEntity();
        untradableInstrument.setSymbol("OLD");
        untradableInstrument.setName("Old Company");
        untradableInstrument.setAssetClass(AssetClass.EQUITY);
        untradableInstrument.setCurrency("USD");
        untradableInstrument.setTradable(false);
    }

    @Nested
    @DisplayName("getInstruments")
    class GetInstruments {
        @Test
        @DisplayName("Returns all instruments when tradable filter is null")
        void returnsAllInstrumentsWhenFilterIsNull() {
            when(instrumentRepository.findAll())
                .thenReturn(List.of(tradableInstrument, untradableInstrument));

            List<InstrumentResponseDTO> result = instrumentService.getInstruments(null);

            assertEquals(2, result.size());
            assertEquals("AAPL", result.get(0).symbol());
            assertEquals("OLD", result.get(1).symbol());
        }

        @Test
        @DisplayName("Returns only tradable instruments when filtered by tradable=true")
        void returnsTradableInstrumentsWhenFilteredByTrue() {
            when(instrumentRepository.findByTradable(true))
                .thenReturn(List.of(tradableInstrument));

            List<InstrumentResponseDTO> result = instrumentService.getInstruments(true);

            assertEquals(1, result.size());
            assertEquals("AAPL", result.get(0).symbol());
            assertEquals(true, result.get(0).tradable());
        }

        @Test
        @DisplayName("Returns only untradable instruments when filtered by tradable=false")
        void returnsUntradableInstrumentsWhenFilteredByFalse() {
            when(instrumentRepository.findByTradable(false))
                .thenReturn(List.of(untradableInstrument));

            List<InstrumentResponseDTO> result = instrumentService.getInstruments(false);

            assertEquals(1, result.size());
            assertEquals("OLD", result.get(0).symbol());
            assertEquals(false, result.get(0).tradable());
        }

        @Test
        @DisplayName("Returns empty list when no instruments match filter")
        void returnsEmptyListWhenNoInstrumentsMatch() {
            when(instrumentRepository.findByTradable(true))
                .thenReturn(List.of());

            List<InstrumentResponseDTO> result = instrumentService.getInstruments(true);

            assertEquals(0, result.size());
        }
    }

    @Nested
    @DisplayName("getInstrument")
    class GetInstrument {
        @Test
        @DisplayName("Returns mapped instrument when found")
        void returnsMappedInstrumentWhenFound() {
            when(instrumentRepository.findBySymbol("AAPL"))
                .thenReturn(Optional.of(tradableInstrument));

            InstrumentResponseDTO result = instrumentService.getInstrument("AAPL");

            assertEquals("AAPL", result.symbol());
            assertEquals("Apple Inc.", result.name());
            assertEquals(AssetClass.EQUITY, result.assetClass());
            assertEquals("USD", result.currency());
            assertEquals(true, result.tradable());
        }

        @Test
        @DisplayName("Throws exception when instrument not found")
        void throwsExceptionWhenInstrumentNotFound() {
            when(instrumentRepository.findBySymbol("INVALID"))
                .thenReturn(Optional.empty());

            assertThrows(InstrumentNotFoundException.class, 
                () -> instrumentService.getInstrument("INVALID"));
        }
    }
}