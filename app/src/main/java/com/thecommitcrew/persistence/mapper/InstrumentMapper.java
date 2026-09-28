package com.thecommitcrew.persistence.mapper;

import org.springframework.stereotype.Component;

import com.thecommitcrew.domain.model.Instrument;
import com.thecommitcrew.domain.validator.InstrumentSymbolValidator;
import com.thecommitcrew.persistence.entity.InstrumentEntity;

@Component
public class InstrumentMapper {

    private final InstrumentSymbolValidator symbolValidator;
    
    public InstrumentMapper(InstrumentSymbolValidator symbolValidator) {
        this.symbolValidator = symbolValidator;
    }
    
    // Convert entity to domain model
    public Instrument toDomain(InstrumentEntity entity) {
        if (entity == null) return null;
        
        return new Instrument(
            entity.getSymbol(),
            entity.getSymbol(),
            entity.getName(),
            entity.getAssetClass(),
            entity.getTradable(),
            symbolValidator
        );
    }
    
    // Convert domain model to entity
    public InstrumentEntity toEntity(Instrument domain) {
        if (domain == null) return null;
        
        return new InstrumentEntity(
            domain.getSymbol(),
            domain.getName(),
            domain.getAssetClass(),
            domain.getCurrency(),
            domain.isTradable()
        );
    }

}