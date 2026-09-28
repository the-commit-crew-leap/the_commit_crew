package com.thecommitcrew.persistence.entity;

import com.thecommitcrew.domain.enums.AssetClass;

import jakarta.persistence.*;

@Entity
@Table(name = "instruments")
public class InstrumentEntity {

    @Id
    @Column(length = 20)
    private String symbol;
    
    @Column(nullable = false)
    private String name;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "asset_class", nullable = false)
    private AssetClass assetClass;
    
    @Column(nullable = false)
    private String currency;
    
    @Column(nullable = false)
    private Boolean tradable;
    
    // Parameterless constructor for JPA
    public InstrumentEntity() {}
    
    public InstrumentEntity(String symbol, String name, AssetClass assetClass, String currency, Boolean tradable) {
        this.symbol = symbol;
        this.name = name;
        this.assetClass = assetClass;
        this.currency = currency;
        this.tradable = tradable;
    }
    
    // Getters and setters
    public String getSymbol() {
        return symbol;
    }
    public void setSymbol(String symbol) {
        this.symbol = symbol;
    }
    
    public String getName() {
        return name;
    }
    public void setName(String name) {
        this.name = name;
    }
    
    public AssetClass getAssetClass() {
        return assetClass;
    }
    public void setAssetClass(AssetClass assetClass) {
        this.assetClass = assetClass;
    }
    
    public String getCurrency() {
        return currency;
    }
    public void setCurrency(String currency) {
        this.currency = currency;
    }
    
    public Boolean getTradable() {
        return tradable;
    }
    public void setTradable(Boolean tradable) {
        this.tradable = tradable;
    }

}
