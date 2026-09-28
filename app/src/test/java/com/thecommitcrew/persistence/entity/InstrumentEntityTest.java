package com.thecommitcrew.persistence.entity;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.thecommitcrew.domain.enums.AssetClass;

@DisplayName("InstrumentEntity Tests")
public class InstrumentEntityTest {

    private static final String TEST_SYMBOL = "AAPL";
    private static final String TEST_NAME = "Apple Inc.";
    private static final AssetClass TEST_ASSET_CLASS = AssetClass.EQUITY;
    private static final String TEST_CURRENCY = "USD";
    private static final Boolean TEST_TRADABLE = true;

    @Nested
    @DisplayName("Constructor")
    class ConstructorTests {

        @Test
        @DisplayName("Creates entity with all parameters")
        void createsEntityWithAllParameters() {
            InstrumentEntity entity = new InstrumentEntity(
                TEST_SYMBOL,
                TEST_NAME,
                TEST_ASSET_CLASS,
                TEST_CURRENCY,
                TEST_TRADABLE
            );

            assertEquals(TEST_SYMBOL, entity.getSymbol());
            assertEquals(TEST_NAME, entity.getName());
            assertEquals(TEST_ASSET_CLASS, entity.getAssetClass());
            assertEquals(TEST_CURRENCY, entity.getCurrency());
            assertEquals(TEST_TRADABLE, entity.getTradable());
        }

        @Test
        @DisplayName("Creates entity with parameterless constructor")
        void createsEntityWithParameterlessConstructor() {
            InstrumentEntity entity = new InstrumentEntity();
            assertNotNull(entity);
        }
    }

    @Nested
    @DisplayName("Getters and Setters")
    class GettersAndSettersTests {

        private InstrumentEntity entity;

        @BeforeEach
        void setUp() {
            entity = new InstrumentEntity(
                TEST_SYMBOL,
                TEST_NAME,
                TEST_ASSET_CLASS,
                TEST_CURRENCY,
                TEST_TRADABLE
            );
        }

        @Test
        @DisplayName("getSymbol returns correct value")
        void getSymbolReturnsCorrectValue() {
            assertEquals(TEST_SYMBOL, entity.getSymbol());
        }

        @Test
        @DisplayName("setSymbol updates value")
        void setSymbolUpdatesValue() {
            String newSymbol = "GOOGL";
            entity.setSymbol(newSymbol);
            assertEquals(newSymbol, entity.getSymbol());
        }

        @Test
        @DisplayName("getName returns correct value")
        void getNameReturnsCorrectValue() {
            assertEquals(TEST_NAME, entity.getName());
        }

        @Test
        @DisplayName("setName updates value")
        void setNameUpdatesValue() {
            String newName = "Google LLC";
            entity.setName(newName);
            assertEquals(newName, entity.getName());
        }

        @Test
        @DisplayName("getAssetClass returns correct value")
        void getAssetClassReturnsCorrectValue() {
            assertEquals(TEST_ASSET_CLASS, entity.getAssetClass());
        }

        @Test
        @DisplayName("setAssetClass updates value")
        void setAssetClassUpdatesValue() {
            AssetClass newAssetClass = AssetClass.BOND;
            entity.setAssetClass(newAssetClass);
            assertEquals(newAssetClass, entity.getAssetClass());
        }

        @Test
        @DisplayName("getCurrency returns correct value")
        void getCurrencyReturnsCorrectValue() {
            assertEquals(TEST_CURRENCY, entity.getCurrency());
        }

        @Test
        @DisplayName("setCurrency updates value")
        void setCurrencyUpdatesValue() {
            String newCurrency = "EUR";
            entity.setCurrency(newCurrency);
            assertEquals(newCurrency, entity.getCurrency());
        }

        @Test
        @DisplayName("getTradable returns correct value")
        void getTradableReturnsCorrectValue() {
            assertEquals(TEST_TRADABLE, entity.getTradable());
        }

        @Test
        @DisplayName("setTradable updates value")
        void setTradableUpdatesValue() {
            entity.setTradable(false);
            assertEquals(false, entity.getTradable());
        }
    }

    @Nested
    @DisplayName("Entity field values")
    class EntityFieldValuesTests {

        @Test
        @DisplayName("Handles different asset classes")
        void handlesDifferentAssetClasses() {
            for (AssetClass assetClass : AssetClass.values()) {
                InstrumentEntity entity = new InstrumentEntity(
                    TEST_SYMBOL,
                    TEST_NAME,
                    assetClass,
                    TEST_CURRENCY,
                    TEST_TRADABLE
                );

                assertEquals(assetClass, entity.getAssetClass());
            }
        }

        @Test
        @DisplayName("Handles non-tradable instruments")
        void handlesNonTradableInstruments() {
            InstrumentEntity entity = new InstrumentEntity(
                TEST_SYMBOL,
                TEST_NAME,
                TEST_ASSET_CLASS,
                TEST_CURRENCY,
                false
            );

            assertEquals(false, entity.getTradable());
        }

        @Test
        @DisplayName("Handles different currency codes")
        void handlesDifferentCurrencyCodes() {
            String[] currencies = { "USD", "EUR", "GBP", "JPY", "CAD" };
            
            for (String currency : currencies) {
                InstrumentEntity entity = new InstrumentEntity(
                    TEST_SYMBOL,
                    TEST_NAME,
                    TEST_ASSET_CLASS,
                    currency,
                    TEST_TRADABLE
                );

                assertEquals(currency, entity.getCurrency());
            }
        }

        @Test
        @DisplayName("Handles long instrument names")
        void handlesLongInstrumentNames() {
            String longName = "A".repeat(255);
            InstrumentEntity entity = new InstrumentEntity(
                TEST_SYMBOL,
                longName,
                TEST_ASSET_CLASS,
                TEST_CURRENCY,
                TEST_TRADABLE
            );

            assertEquals(longName, entity.getName());
        }

        @Test
        @DisplayName("Handles short stock symbols")
        void handlesShortStockSymbols() {
            String shortSymbol = "T";
            InstrumentEntity entity = new InstrumentEntity(
                shortSymbol,
                TEST_NAME,
                TEST_ASSET_CLASS,
                TEST_CURRENCY,
                TEST_TRADABLE
            );

            assertEquals(shortSymbol, entity.getSymbol());
        }

        @Test
        @DisplayName("Handles long stock symbols")
        void handlesLongStockSymbols() {
            String longSymbol = "ABCDE";
            InstrumentEntity entity = new InstrumentEntity(
                longSymbol,
                TEST_NAME,
                TEST_ASSET_CLASS,
                TEST_CURRENCY,
                TEST_TRADABLE
            );

            assertEquals(longSymbol, entity.getSymbol());
        }
    }

    @Nested
    @DisplayName("Field immutability for ID")
    class FieldImmutabilityTests {

        @Test
        @DisplayName("Symbol acts as primary key")
        void symbolActsAsPrimaryKey() {
            InstrumentEntity entity = new InstrumentEntity(
                TEST_SYMBOL,
                TEST_NAME,
                TEST_ASSET_CLASS,
                TEST_CURRENCY,
                TEST_TRADABLE
            );

            // Symbol should not change in a real scenario
            assertEquals(TEST_SYMBOL, entity.getSymbol());
        }
    }

    @Nested
    @DisplayName("JPA annotations")
    class JPAAnnotationsTests {

        @Test
        @DisplayName("Entity has @Entity annotation")
        void hasEntityAnnotation() {
            assertTrue(InstrumentEntity.class.isAnnotationPresent(jakarta.persistence.Entity.class));
        }

        @Test
        @DisplayName("Entity has @Table annotation")
        void hasTableAnnotation() {
            assertTrue(InstrumentEntity.class.isAnnotationPresent(jakarta.persistence.Table.class));
        }
    }

    @Nested
    @DisplayName("Multiple instruments")
    class MultipleInstrumentsTests {

        @Test
        @DisplayName("Creates multiple independent instruments")
        void createsMultipleIndependentInstruments() {
            InstrumentEntity entity1 = new InstrumentEntity("AAPL", "Apple", TEST_ASSET_CLASS, "USD", true);
            InstrumentEntity entity2 = new InstrumentEntity("GOOGL", "Google", TEST_ASSET_CLASS, "USD", true);
            InstrumentEntity entity3 = new InstrumentEntity("MSFT", "Microsoft", TEST_ASSET_CLASS, "USD", false);

            assertEquals("AAPL", entity1.getSymbol());
            assertEquals("GOOGL", entity2.getSymbol());
            assertEquals("MSFT", entity3.getSymbol());

            assertEquals("Apple", entity1.getName());
            assertEquals("Google", entity2.getName());
            assertEquals("Microsoft", entity3.getName());

            assertTrue(entity1.getTradable());
            assertTrue(entity2.getTradable());
            assertFalse(entity3.getTradable());
        }
    }
}
