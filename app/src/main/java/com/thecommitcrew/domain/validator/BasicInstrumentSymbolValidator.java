package com.thecommitcrew.domain.validator;

import org.springframework.stereotype.Component;

import com.thecommitcrew.domain.exception.InstrumentNotFoundException;
import com.thecommitcrew.persistence.repository.InstrumentRepository;

/**
 * Basic Validator - checks if the symbol belongs to the valid symbols set.
 */
@Component 
public class BasicInstrumentSymbolValidator implements InstrumentSymbolValidator {

    private final InstrumentRepository instrumentRepository;

    public BasicInstrumentSymbolValidator(InstrumentRepository instrumentRepository) {
        this.instrumentRepository = instrumentRepository;
    }

    @Override
    public void validateSymbol(String symbol) {
        if (!instrumentRepository.findBySymbol(symbol).isPresent()) {
            throw new InstrumentNotFoundException(symbol + " is not a valid instrument symbol");
        }
    }

}
