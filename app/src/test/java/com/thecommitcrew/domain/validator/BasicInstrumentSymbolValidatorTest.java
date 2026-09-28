package com.thecommitcrew.domain.validator;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.thecommitcrew.domain.exception.InstrumentNotFoundException;
import com.thecommitcrew.persistence.entity.InstrumentEntity;
import com.thecommitcrew.persistence.repository.InstrumentRepository;

@ExtendWith(MockitoExtension.class)
public class BasicInstrumentSymbolValidatorTest {

    @Mock
    private InstrumentRepository instrumentRepository;

    private InstrumentSymbolValidator validator;

    @BeforeEach
    void setUp() {
        validator = new BasicInstrumentSymbolValidator(instrumentRepository);
    }
    
    @Test
    public void testValidateSymbol_ValidSymbol() {
        // Mock valid symbols
        when(instrumentRepository.findBySymbol("AAPL"))
            .thenReturn(Optional.of(new InstrumentEntity("AAPL", "Apple Inc.", null, "USD", true)));
        
        assertDoesNotThrow(() -> {
            validator.validateSymbol("AAPL");
        });
    }
    
    @Test
    public void testValidateSymbol_InvalidSymbol() {
        // Mock invalid symbol - returns empty Optional
        when(instrumentRepository.findBySymbol("NONEXISTENT"))
            .thenReturn(Optional.empty());
        
        assertThrows(InstrumentNotFoundException.class, () -> {
            validator.validateSymbol("NONEXISTENT");
        });
    }

}
