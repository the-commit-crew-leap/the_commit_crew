package com.thecommitcrew.domain.model;

import com.thecommitcrew.domain.enums.AssetClass;
import com.thecommitcrew.domain.validator.InstrumentSymbolValidator;

/**
 * Represents a financial trading instrument (e.g., stock, cryptocurrency, commodity).
 * This model encapsulates the properties needed to identify, describe,
 * and determine the tradable status of an asset.
 */
public class Instrument {
    
    private final String id;
    private final String symbol;
    private final String name;
    private final AssetClass assetClass;
    private final String currency = "USD";
    private final boolean tradable;


    public Instrument(String id, String symbol, String name, AssetClass assetClass,
        boolean tradable, InstrumentSymbolValidator validator) {
        this.id = validateNotNull(id, "ID cannot be null");
        this.name = validateNotBlank(name, "Name cannot be null");
        this.assetClass = validateNotNull(assetClass, "Asset class cannot be null");
        this.tradable = tradable;
        validateNotNull(validator, "Validator cannot be null");
        validator.validateSymbol(symbol);
        this.symbol = symbol;
    }

    public String getId() {
        return id;
    }

    public String getSymbol() {
        return symbol;
    }

    public String getName() {
        return name;
    }

    public AssetClass getAssetClass() {
        return assetClass;
    }

    public String getCurrency() {
        return currency;
    }

    // TODO: change this method once we know what makes an Instrument tradable
    public Boolean isTradable() {
        return tradable;
    }

    @Override
    public String toString() {
        return "Instrument [id=" + id + ", symbol=" + symbol + ", name=" + name + ", assetClass=" + assetClass
                + ", currency=" + currency + ", isTradable=" + tradable + "]";
    }

    private static <T> T validateNotNull(T value, String message) {
        if (value == null) throw new IllegalArgumentException(message);
        return value;
    }

    private static String validateNotBlank(String value, String message) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(message);
        return value;
    }

}