package com.thecommitcrew.persistence.mapper;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.thecommitcrew.domain.enums.AssetClass;
import com.thecommitcrew.domain.model.Instrument;
import com.thecommitcrew.domain.validator.BasicInstrumentSymbolValidator;
import com.thecommitcrew.domain.validator.InstrumentSymbolValidator;
import com.thecommitcrew.persistence.entity.InstrumentEntity;
import com.thecommitcrew.persistence.repository.InstrumentRepository;

@ExtendWith(MockitoExtension.class)
@DisplayName("InstrumentMapper Tests")
public class InstrumentMapperTest {

    @Mock
    private InstrumentRepository instrumentRepository;

    private InstrumentMapper mapper;
    private InstrumentSymbolValidator symbolValidator;
    private static final String TEST_SYMBOL = "AAPL";
    private static final String TEST_NAME = "Apple Inc.";
    private static final AssetClass TEST_ASSET_CLASS = AssetClass.EQUITY;
    private static final String TEST_CURRENCY = "USD";
    private static final Boolean TEST_TRADABLE = true;

    @BeforeEach
    void setUp() {
        symbolValidator = new BasicInstrumentSymbolValidator(instrumentRepository);
        mapper = new InstrumentMapper(symbolValidator);
    }

    @Nested
    @DisplayName("toDomain()")
    class ToDomainTests {

        @Test
        @DisplayName("Converts InstrumentEntity to Instrument successfully")
        void convertEntityToDomainSuccessfully() {
            // Mock valid symbol
        when(instrumentRepository.findBySymbol("AAPL"))
            .thenReturn(Optional.of(new InstrumentEntity("AAPL", "Apple Inc.", null, "USD", true)));
            
            InstrumentEntity entity = new InstrumentEntity(
                TEST_SYMBOL,
                TEST_NAME,
                TEST_ASSET_CLASS,
                TEST_CURRENCY,
                TEST_TRADABLE
            );

            Instrument result = mapper.toDomain(entity);

            assertNotNull(result);
            assertEquals(TEST_SYMBOL, result.getSymbol());
            assertEquals(TEST_NAME, result.getName());
            assertEquals(TEST_ASSET_CLASS, result.getAssetClass());
            assertEquals(TEST_TRADABLE, result.isTradable());
        }

        @Test
        @DisplayName("Returns null when entity is null")
        void returnNullWhenEntityIsNull() {
            Instrument result = mapper.toDomain(null);
            assertNull(result);
        }

        @Test
        @DisplayName("Maps symbol correctly")
        void mapsSymbolCorrectly() {
            // Mock valid symbol
            when(instrumentRepository.findBySymbol("GOOGL"))    
                .thenReturn(Optional.of(new InstrumentEntity("GOOGL", "Alphabet Inc.", null, "USD", true)));

            String symbol = "GOOGL";
            InstrumentEntity entity = new InstrumentEntity(
                symbol,
                TEST_NAME,
                TEST_ASSET_CLASS,
                TEST_CURRENCY,
                TEST_TRADABLE
            );

            Instrument result = mapper.toDomain(entity);

            assertEquals(symbol, result.getSymbol());
        }

        @Test
        @DisplayName("Maps name correctly")
        void mapsNameCorrectly() {
            // Mock valid symbol
            // Mock valid symbol
            when(instrumentRepository.findBySymbol("AAPL"))
                .thenReturn(Optional.of(new InstrumentEntity("AAPL", "Apple Inc.", null, "USD", true)));

            String name = "Microsoft Corporation";
            InstrumentEntity entity = new InstrumentEntity(
                TEST_SYMBOL,
                name,
                TEST_ASSET_CLASS,
                TEST_CURRENCY,
                TEST_TRADABLE
            );

            Instrument result = mapper.toDomain(entity);

            assertEquals(name, result.getName());
        }

        @Test
        @DisplayName("Maps different asset classes correctly")
        void mapsAssetClassesCorrectly() {
            // Mock valid symbol
            when(instrumentRepository.findBySymbol("AAPL"))
                .thenReturn(Optional.of(new InstrumentEntity("AAPL", "Apple Inc.", null, "USD", true)));

            for (AssetClass assetClass : AssetClass.values()) {
                InstrumentEntity entity = new InstrumentEntity(
                    TEST_SYMBOL,
                    TEST_NAME,
                    assetClass,
                    TEST_CURRENCY,
                    TEST_TRADABLE
                );

                Instrument result = mapper.toDomain(entity);

                assertEquals(assetClass, result.getAssetClass());
            }
        }

        @Test
        @DisplayName("Maps tradable flag correctly")
        void mapsTradableFlagCorrectly() {
            // Mock valid symbol
            when(instrumentRepository.findBySymbol("AAPL"))
                .thenReturn(Optional.of(new InstrumentEntity("AAPL", "Apple Inc.", null, "USD", true)));


            InstrumentEntity entity = new InstrumentEntity(
                TEST_SYMBOL,
                TEST_NAME,
                TEST_ASSET_CLASS,
                TEST_CURRENCY,
                false
            );

            Instrument result = mapper.toDomain(entity);

            assertEquals(false, result.isTradable());
        }

        @Test
        @DisplayName("Verifies currency is set to USD in domain model")
        void verifiesCurrencyIsUSD() {
            // Mock valid symbol
            when(instrumentRepository.findBySymbol("AAPL"))
                .thenReturn(Optional.of(new InstrumentEntity("AAPL", "Apple Inc.", null, "USD", true)));


            InstrumentEntity entity = new InstrumentEntity(
                TEST_SYMBOL,
                TEST_NAME,
                TEST_ASSET_CLASS,
                "EUR",
                TEST_TRADABLE
            );

            Instrument result = mapper.toDomain(entity);

            assertEquals("USD", result.getCurrency());
        }
    }

    @Nested
    @DisplayName("toEntity()")
    class ToEntityTests {

        @Test
        @DisplayName("Converts Instrument to InstrumentEntity successfully")
        void convertDomainToEntitySuccessfully() {
            // Mock valid symbol
            when(instrumentRepository.findBySymbol("AAPL"))
                .thenReturn(Optional.of(new InstrumentEntity("AAPL", "Apple Inc.", null, "USD", true)));

            Instrument domain = new Instrument(
                TEST_SYMBOL,
                TEST_SYMBOL,
                TEST_NAME,
                TEST_ASSET_CLASS,
                TEST_TRADABLE,
                symbolValidator
            );

            InstrumentEntity result = mapper.toEntity(domain);

            assertNotNull(result);
            assertEquals(TEST_SYMBOL, result.getSymbol());
            assertEquals(TEST_NAME, result.getName());
            assertEquals(TEST_ASSET_CLASS, result.getAssetClass());
            assertEquals(TEST_TRADABLE, result.getTradable());
        }

        @Test
        @DisplayName("Returns null when domain is null")
        void returnNullWhenDomainIsNull() {
            InstrumentEntity result = mapper.toEntity(null);
            assertNull(result);
        }

        @Test
        @DisplayName("Maps symbol from domain correctly")
        void mapsSymbolFromDomainCorrectly() {
            // Mock valid symbol
            when(instrumentRepository.findBySymbol("MSFT"))
                .thenReturn(Optional.of(new InstrumentEntity("MSFT", "Microsoft Corporation", null, "USD", true)));
            
            String symbol = "MSFT";
            Instrument domain = new Instrument(
                symbol,
                symbol,
                TEST_NAME,
                TEST_ASSET_CLASS,
                TEST_TRADABLE,
                symbolValidator
            );

            InstrumentEntity result = mapper.toEntity(domain);

            assertEquals(symbol, result.getSymbol());
        }

        @Test
        @DisplayName("Maps name from domain correctly")
        void mapsNameFromDomainCorrectly() {
            // Mock valid symbol
            when(instrumentRepository.findBySymbol("AAPL"))
                .thenReturn(Optional.of(new InstrumentEntity("AAPL", "Apple Inc.", null, "USD", true)));

            String name = "Tesla Inc.";
            Instrument domain = new Instrument(
                TEST_SYMBOL,
                TEST_SYMBOL,
                name,
                TEST_ASSET_CLASS,
                TEST_TRADABLE,
                symbolValidator
            );

            InstrumentEntity result = mapper.toEntity(domain);

            assertEquals(name, result.getName());
        }

        @Test
        @DisplayName("Maps tradable status from domain correctly")
        void mapsTradableStatusFromDomainCorrectly() {
            // Mock valid symbol
            when(instrumentRepository.findBySymbol("AAPL"))
                .thenReturn(Optional.of(new InstrumentEntity("AAPL", "Apple Inc.", null, "USD", true)));

            Instrument domain = new Instrument(
                TEST_SYMBOL,
                TEST_SYMBOL,
                TEST_NAME,
                TEST_ASSET_CLASS,
                false,
                symbolValidator
            );

            InstrumentEntity result = mapper.toEntity(domain);

            assertEquals(false, result.getTradable());
        }

        @Test
        @DisplayName("Maps currency from domain correctly")
        void mapsCurrencyFromDomainCorrectly() {
            // Mock valid symbol
            when(instrumentRepository.findBySymbol("AAPL"))
                .thenReturn(Optional.of(new InstrumentEntity("AAPL", "Apple Inc.", null, "USD", true)));

            Instrument domain = new Instrument(
                TEST_SYMBOL,
                TEST_SYMBOL,
                TEST_NAME,
                TEST_ASSET_CLASS,
                TEST_TRADABLE,
                symbolValidator
            );

            InstrumentEntity result = mapper.toEntity(domain);

            assertEquals("USD", result.getCurrency());
        }
    }

    @Nested
    @DisplayName("Bidirectional mapping")
    class BidirectionalMappingTests {

        @Test
        @DisplayName("Entity to Domain to Entity preserves data")
        void entityToDomainToEntityPreservesData() {
            // Mock valid symbol
            when(instrumentRepository.findBySymbol("AAPL"))
                .thenReturn(Optional.of(new InstrumentEntity("AAPL", "Apple Inc.", null, "USD", true)));

            InstrumentEntity original = new InstrumentEntity(
                TEST_SYMBOL,
                TEST_NAME,
                TEST_ASSET_CLASS,
                TEST_CURRENCY,
                TEST_TRADABLE
            );

            Instrument domain = mapper.toDomain(original);
            InstrumentEntity result = mapper.toEntity(domain);

            assertEquals(original.getSymbol(), result.getSymbol());
            assertEquals(original.getName(), result.getName());
            assertEquals(original.getAssetClass(), result.getAssetClass());
            assertEquals(original.getTradable(), result.getTradable());
        }
    }
}
