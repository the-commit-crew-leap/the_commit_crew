package com.thecommitcrew.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
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

    private static final String TEST_SYMBOL = "AAPL";

    @Mock
    private InstrumentRepository instrumentRepository;

    private InstrumentService instrumentService;
    private InstrumentEntity instrumentEntity;

    @BeforeEach
    void setUp() {
        instrumentService = new InstrumentService(instrumentRepository);
        instrumentEntity = new InstrumentEntity(
            TEST_SYMBOL,
            "Apple Inc.",
            AssetClass.EQUITY,
            "USD",
            true
        );
    }

    @Test
    void getInstruments_returnsMappedInstrumentList() {
        when(instrumentRepository.findAll()).thenReturn(List.of(instrumentEntity));

        List<InstrumentResponseDTO> result = instrumentService.getInstruments();

        assertEquals(1, result.size());
        assertEquals(TEST_SYMBOL, result.get(0).symbol());
        assertEquals("Apple Inc.", result.get(0).name());
        assertEquals(AssetClass.EQUITY, result.get(0).assetClass());
        assertEquals("USD", result.get(0).currency());
        assertEquals(true, result.get(0).tradable());
    }

    @Test
    void getInstrument_returnsMappedInstrumentWhenFound() {
        when(instrumentRepository.findBySymbol(TEST_SYMBOL)).thenReturn(Optional.of(instrumentEntity));

        InstrumentResponseDTO result = instrumentService.getInstrument(TEST_SYMBOL);

        assertEquals(TEST_SYMBOL, result.symbol());
        assertEquals("Apple Inc.", result.name());
        assertEquals(AssetClass.EQUITY, result.assetClass());
        assertEquals("USD", result.currency());
        assertEquals(true, result.tradable());
    }

    @Test
    void getInstrument_throwsWhenInstrumentNotFound() {
        when(instrumentRepository.findBySymbol(TEST_SYMBOL)).thenReturn(Optional.empty());

        InstrumentNotFoundException exception = assertThrows(
            InstrumentNotFoundException.class,
            () -> instrumentService.getInstrument(TEST_SYMBOL)
        );

        assertEquals("Instrument not found for symbol: " + TEST_SYMBOL, exception.getMessage());
    }
}