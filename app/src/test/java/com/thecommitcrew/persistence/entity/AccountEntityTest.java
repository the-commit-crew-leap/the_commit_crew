package com.thecommitcrew.persistence.entity;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.thecommitcrew.domain.enums.AccountStatus;

@DisplayName("AccountEntity Tests")
public class AccountEntityTest {

    private static final String TEST_ACCOUNT_ID = "ACC001";
    private static final String TEST_HOLDER_NAME = "Jane Smith";
    private static final BigDecimal TEST_BALANCE = new BigDecimal("50000.00");
    private static final AccountStatus TEST_STATUS = AccountStatus.ACTIVE;
    private static final int TEST_VERSION = 1;
    private LocalDateTime testTime;

    @BeforeEach
    void setUp() {
        testTime = LocalDateTime.now();
    }

    @Nested
    @DisplayName("Constructor")
    class ConstructorTests {

        @Test
        @DisplayName("Creates entity with all parameters")
        void createsEntityWithAllParameters() {
            AccountEntity entity = new AccountEntity(
                TEST_ACCOUNT_ID,
                TEST_HOLDER_NAME,
                TEST_BALANCE,
                TEST_STATUS,
                TEST_VERSION,
                testTime
            );

            assertEquals(TEST_ACCOUNT_ID, entity.getAccountId());
            assertEquals(TEST_HOLDER_NAME, entity.getHolderName());
            assertEquals(TEST_BALANCE, entity.getCashBalance());
            assertEquals(TEST_STATUS, entity.getStatus());
            assertEquals(TEST_VERSION, entity.getVersion());
            assertEquals(testTime, entity.getLastUpdated());
        }

        @Test
        @DisplayName("Creates entity with parameterless constructor")
        void createsEntityWithParameterlessConstructor() {
            AccountEntity entity = new AccountEntity();
            assertNotNull(entity);
        }
    }

    @Nested
    @DisplayName("Getters and Setters")
    class GettersAndSettersTests {

        private AccountEntity entity;

        @BeforeEach
        void setUp() {
            entity = new AccountEntity(
                TEST_ACCOUNT_ID,
                TEST_HOLDER_NAME,
                TEST_BALANCE,
                TEST_STATUS,
                TEST_VERSION,
                testTime
            );
        }

        @Test
        @DisplayName("getId returns null for new entity")
        void getIdReturnsNull() {
            assertNull(entity.getId());
        }

        @Test
        @DisplayName("getAccountId returns correct value")
        void getAccountIdReturnsCorrectValue() {
            assertEquals(TEST_ACCOUNT_ID, entity.getAccountId());
        }

        @Test
        @DisplayName("setAccountId updates value")
        void setAccountIdUpdatesValue() {
            String newAccountId = "ACC002";
            entity.setAccountId(newAccountId);
            assertEquals(newAccountId, entity.getAccountId());
        }

        @Test
        @DisplayName("getHolderName returns correct value")
        void getHolderNameReturnsCorrectValue() {
            assertEquals(TEST_HOLDER_NAME, entity.getHolderName());
        }

        @Test
        @DisplayName("setHolderName updates value")
        void setHolderNameUpdatesValue() {
            String newName = "John Doe";
            entity.setHolderName(newName);
            assertEquals(newName, entity.getHolderName());
        }

        @Test
        @DisplayName("getCashBalance returns correct value")
        void getCashBalanceReturnsCorrectValue() {
            assertEquals(TEST_BALANCE, entity.getCashBalance());
        }

        @Test
        @DisplayName("setCashBalance updates value")
        void setCashBalanceUpdatesValue() {
            BigDecimal newBalance = new BigDecimal("75000.00");
            entity.setCashBalance(newBalance);
            assertEquals(newBalance, entity.getCashBalance());
        }

        @Test
        @DisplayName("getStatus returns correct value")
        void getStatusReturnsCorrectValue() {
            assertEquals(TEST_STATUS, entity.getStatus());
        }

        @Test
        @DisplayName("setStatus updates value")
        void setStatusUpdatesValue() {
            entity.setStatus(AccountStatus.SUSPENDED);
            assertEquals(AccountStatus.SUSPENDED, entity.getStatus());
        }

        @Test
        @DisplayName("getVersion returns correct value")
        void getVersionReturnsCorrectValue() {
            assertEquals(TEST_VERSION, entity.getVersion());
        }

        @Test
        @DisplayName("setVersion updates value")
        void setVersionUpdatesValue() {
            int newVersion = 2;
            entity.setVersion(newVersion);
            assertEquals(newVersion, entity.getVersion());
        }

        @Test
        @DisplayName("getLastUpdated returns correct value")
        void getLastUpdatedReturnsCorrectValue() {
            assertEquals(testTime, entity.getLastUpdated());
        }

        @Test
        @DisplayName("setLastUpdated updates value")
        void setLastUpdatedUpdatesValue() {
            LocalDateTime newTime = LocalDateTime.now().plusDays(1);
            entity.setLastUpdated(newTime);
            assertEquals(newTime, entity.getLastUpdated());
        }
    }

    @Nested
    @DisplayName("Entity field values")
    class EntityFieldValuesTests {

        @Test
        @DisplayName("Handles zero balance correctly")
        void handlesZeroBalance() {
            AccountEntity entity = new AccountEntity(
                TEST_ACCOUNT_ID,
                TEST_HOLDER_NAME,
                BigDecimal.ZERO,
                TEST_STATUS,
                TEST_VERSION,
                testTime
            );

            assertEquals(BigDecimal.ZERO, entity.getCashBalance());
        }

        @Test
        @DisplayName("Handles negative balance")
        void handlesNegativeBalance() {
            BigDecimal negativeBalance = new BigDecimal("-1000.00");
            AccountEntity entity = new AccountEntity(
                TEST_ACCOUNT_ID,
                TEST_HOLDER_NAME,
                negativeBalance,
                TEST_STATUS,
                TEST_VERSION,
                testTime
            );

            assertEquals(negativeBalance, entity.getCashBalance());
        }

        @Test
        @DisplayName("Handles large balance values")
        void handlesLargeBalanceValues() {
            BigDecimal largeBalance = new BigDecimal("999999999.99");
            AccountEntity entity = new AccountEntity(
                TEST_ACCOUNT_ID,
                TEST_HOLDER_NAME,
                largeBalance,
                TEST_STATUS,
                TEST_VERSION,
                testTime
            );

            assertEquals(largeBalance, entity.getCashBalance());
        }

        @Test
        @DisplayName("Handles different account statuses")
        void handlesDifferentAccountStatuses() {
            for (AccountStatus status : AccountStatus.values()) {
                AccountEntity entity = new AccountEntity(
                    TEST_ACCOUNT_ID,
                    TEST_HOLDER_NAME,
                    TEST_BALANCE,
                    status,
                    TEST_VERSION,
                    testTime
                );

                assertEquals(status, entity.getStatus());
            }
        }

        @Test
        @DisplayName("Handles long holder names")
        void handlesLongHolderNames() {
            String longName = "A".repeat(255);
            AccountEntity entity = new AccountEntity(
                TEST_ACCOUNT_ID,
                longName,
                TEST_BALANCE,
                TEST_STATUS,
                TEST_VERSION,
                testTime
            );

            assertEquals(longName, entity.getHolderName());
        }

        @Test
        @DisplayName("Handles version increments")
        void handlesVersionIncrements() {
            AccountEntity entity = new AccountEntity(
                TEST_ACCOUNT_ID,
                TEST_HOLDER_NAME,
                TEST_BALANCE,
                TEST_STATUS,
                1,
                testTime
            );

            entity.setVersion(2);
            entity.setVersion(3);

            assertEquals(3, entity.getVersion());
        }
    }

    @Nested
    @DisplayName("JPA annotations")
    class JPAAnnotationsTests {

        @Test
        @DisplayName("Entity has @Entity annotation")
        void hasEntityAnnotation() {
            assertTrue(AccountEntity.class.isAnnotationPresent(jakarta.persistence.Entity.class));
        }

        @Test
        @DisplayName("Entity has @Table annotation")
        void hasTableAnnotation() {
            assertTrue(AccountEntity.class.isAnnotationPresent(jakarta.persistence.Table.class));
        }
    }
}
