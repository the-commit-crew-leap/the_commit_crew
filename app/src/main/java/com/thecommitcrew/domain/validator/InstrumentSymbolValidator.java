package com.thecommitcrew.domain.validator;

/**
 * Interface for validating instrument ticker before creating a new object.
 */
public interface InstrumentSymbolValidator {

    /**
     * Validates if the symbol represents a valid instrument.
     * @throws InstrumentNotFoundException if symbol is invalid
     */
    void validateSymbol(String symbol);
}
