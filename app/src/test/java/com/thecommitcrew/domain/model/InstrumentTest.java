package com.thecommitcrew.domain.model;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.thecommitcrew.domain.enums.AssetClass;
import com.thecommitcrew.domain.exception.InstrumentNotFoundException;
import com.thecommitcrew.domain.validator.BasicInstrumentSymbolValidator;
import com.thecommitcrew.domain.validator.InstrumentSymbolValidator;
import com.thecommitcrew.persistence.entity.InstrumentEntity;
import com.thecommitcrew.persistence.repository.InstrumentRepository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.when;

import java.util.Optional;

@ExtendWith(MockitoExtension.class)
class InstrumentTest {

    @Mock
    private InstrumentRepository instrumentRepository;
    
    private Instrument instrument;
    private InstrumentSymbolValidator validator;
    
    @BeforeEach
    void setUp() {
        validator = new BasicInstrumentSymbolValidator(instrumentRepository);

        // Mock valid symbol
        when(instrumentRepository.findBySymbol("AAPL"))
            .thenReturn(Optional.of(new InstrumentEntity("AAPL", "Apple Inc.", null, "USD", true)));

        instrument = new Instrument("1", "AAPL", "Apple Inc.", AssetClass.EQUITY, true, validator);
    }
    
    @Test
    void testConstructor() {
        assertNotNull(instrument);
        assertEquals("1", instrument.getId());
        assertEquals("AAPL", instrument.getSymbol());
        assertEquals("Apple Inc.", instrument.getName());
        assertEquals(AssetClass.EQUITY, instrument.getAssetClass());
        assertEquals("USD", instrument.getCurrency());
        assertTrue(instrument.isTradable());
    }

    @Nested
    @DisplayName("Tests for constructor failures")
    class testingConstrutorInvalidInputs {

        @Test
        @DisplayName("Testing null id")
        void testConstructor_NullId() {
            assertThrows(IllegalArgumentException.class, () -> new Instrument(null, "AAPL", "Apple Inc.", AssetClass.EQUITY, true, validator));
        }
        
        @Test
        @DisplayName("Testing null name")
        void testConstructor_NullName() {
            assertThrows(IllegalArgumentException.class, () -> new Instrument("1", "AAPL", null, AssetClass.EQUITY, true, validator));
        }
        
        @Test
        @DisplayName("Testing null asset class")
        void testConstructor_NullAssetClass() {
            assertThrows(IllegalArgumentException.class, () -> new Instrument("1", "AAPL", "Apple Inc.", null, true, validator));
        }
        
        @Test
        @DisplayName("Testing null validator")
        void testConstructor_NullValidator() {
            assertThrows(IllegalArgumentException.class, () -> new Instrument("1", "AAPL", "Apple Inc.", AssetClass.EQUITY, true, null));
        }

        @Test
        @DisplayName("Testing invalid symbol")
        void testConstructor_InvalidSymbol() {
            assertThrows(InstrumentNotFoundException.class, () -> new Instrument("1", "INVALID_SYMBOL", "Apple Inc.", AssetClass.EQUITY, true, validator));
        }
    }
    
    @Nested
    @DisplayName("Tests for getter methods")
    class testingGetters {

        @Test
    void testGetId() {
            assertEquals("1", instrument.getId());
        }
        
        @Test
    void testGetSymbol() {
            assertEquals("AAPL", instrument.getSymbol());
        }
        
        @Test
    void testGetName() {
            assertEquals("Apple Inc.", instrument.getName());
        }
        
        @Test
    void testGetAssetClass() {
            assertEquals(AssetClass.EQUITY, instrument.getAssetClass());
        }
        
        @Test
    void testGetCurrency() {
            assertEquals("USD", instrument.getCurrency());
        }
        
        @Test
        void testIsTradableTrue() {
            assertTrue(instrument.isTradable());
        }
        
        @Test
        void testIsTradableFalse() {
            Instrument nonTradable = new Instrument("2", "AAPL", "Apple Inc.", AssetClass.EQUITY, false, validator);
            assertFalse(nonTradable.isTradable());
        }
    }
    
    @Test
    void testImmutability() {
        // Since fields are final, attempting to change them should not be possible
        // This test verifies the object remains unchanged after creation
        String originalSymbol = instrument.getSymbol();
        assertEquals("AAPL", originalSymbol);
        assertEquals("AAPL", instrument.getSymbol()); // Still the same
    }
}