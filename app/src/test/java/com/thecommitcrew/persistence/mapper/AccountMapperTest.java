package com.thecommitcrew.persistence.mapper;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.thecommitcrew.domain.enums.AccountStatus;
import com.thecommitcrew.domain.model.Account;
import com.thecommitcrew.domain.model.Money;
import com.thecommitcrew.domain.validator.AccountStatusValidator;
import com.thecommitcrew.domain.validator.DefaultAccountStatusValidator;
import com.thecommitcrew.persistence.entity.AccountEntity;

@DisplayName("AccountMapper Tests")
public class AccountMapperTest {

    private AccountMapper mapper;
    private AccountStatusValidator statusValidator;
    private static final Long TEST_ACCOUNT_ID = 1L;
    private static final String TEST_HOLDER_NAME = "John Doe";
    private static final BigDecimal TEST_BALANCE = new BigDecimal("5000.00");
    private static final AccountStatus TEST_STATUS = AccountStatus.ACTIVE;
    private static final int TEST_VERSION = 1;

    @BeforeEach
    void setUp() {
        statusValidator = new DefaultAccountStatusValidator();
        mapper = new AccountMapper(statusValidator);
    }

    @Nested
    @DisplayName("toDomain()")
    class ToDomainTests {

        @Test
        @DisplayName("Converts AccountEntity to Account successfully")
        void convertEntityToDomainSuccessfully() {
            LocalDateTime now = LocalDateTime.now();
            AccountEntity entity = new AccountEntity(
                TEST_ACCOUNT_ID.toString(),
                TEST_HOLDER_NAME,
                TEST_BALANCE,
                TEST_STATUS,
                TEST_VERSION,
                now
            );
            entity.setId(TEST_ACCOUNT_ID);

            Account result = mapper.toDomain(entity);

            assertNotNull(result);
            assertEquals(TEST_ACCOUNT_ID, result.getAccountId());
            assertEquals(TEST_HOLDER_NAME, result.getHolderName());
            assertEquals(TEST_BALANCE, result.getCashBalance().getAmount());
            assertEquals(TEST_STATUS, result.getStatus());
            assertEquals(TEST_VERSION, result.getVersion());
            assertEquals(now, result.getLastUpdated());
        }

        @Test
        @DisplayName("Returns null when entity is null")
        void returnNullWhenEntityIsNull() {
            Account result = mapper.toDomain(null);
            assertNull(result);
        }

        @Test
        @DisplayName("Correctly maps cash balance to Money object")
        void mapsCashBalanceToMoney() {
            BigDecimal balance = new BigDecimal("12345.67");
            AccountEntity entity = new AccountEntity(
                "1",
                TEST_HOLDER_NAME,
                balance,
                TEST_STATUS,
                TEST_VERSION,
                LocalDateTime.now()
            );
            entity.setId(1L);

            Account result = mapper.toDomain(entity);

            assertNotNull(result.getCashBalance());
            assertEquals(balance, result.getCashBalance().getAmount());
        }

        @Test
        @DisplayName("Maps different account statuses correctly")
        void mapsAccountStatusesCorrectly() {
            for (AccountStatus status : AccountStatus.values()) {
                AccountEntity entity = new AccountEntity(
                    "1",
                    TEST_HOLDER_NAME,
                    TEST_BALANCE,
                    status,
                    TEST_VERSION,
                    LocalDateTime.now()
                );
                entity.setId(1L);

                Account result = mapper.toDomain(entity);

                assertEquals(status, result.getStatus());
            }
        }

        @Test
        @DisplayName("Maps zero balance correctly")
        void mapsZeroBalance() {
            AccountEntity entity = new AccountEntity(
                "1",
                TEST_HOLDER_NAME,
                BigDecimal.ZERO,
                TEST_STATUS,
                TEST_VERSION,
                LocalDateTime.now()
            );
            entity.setId(1L);

            Account result = mapper.toDomain(entity);

            assertEquals(BigDecimal.ZERO, result.getCashBalance().getAmount());
        }
    }

    @Nested
    @DisplayName("toEntity()")
    class ToEntityTests {

        @Test
        @DisplayName("Converts Account to AccountEntity successfully")
        void convertDomainToEntitySuccessfully() {
            LocalDateTime now = LocalDateTime.now();
            Account domain = new Account(
                TEST_ACCOUNT_ID,
                TEST_HOLDER_NAME,
                new Money(TEST_BALANCE),
                TEST_STATUS,
                TEST_VERSION,
                now,
                statusValidator
            );

            AccountEntity result = mapper.toEntity(domain);

            assertNotNull(result);
            assertEquals(TEST_ACCOUNT_ID.toString(), result.getAccountId());
            assertEquals(TEST_HOLDER_NAME, result.getHolderName());
            assertEquals(TEST_BALANCE, result.getCashBalance());
            assertEquals(TEST_STATUS, result.getStatus());
            assertEquals(TEST_VERSION, result.getVersion());
            assertEquals(now, result.getLastUpdated());
        }

        @Test
        @DisplayName("Returns null when domain is null")
        void returnNullWhenDomainIsNull() {
            AccountEntity result = mapper.toEntity(null);
            assertNull(result);
        }

        @Test
        @DisplayName("Correctly converts account ID to string")
        void convertsAccountIdToString() {
            Long accountId = 999L;
            Account domain = new Account(
                accountId,
                TEST_HOLDER_NAME,
                new Money(TEST_BALANCE),
                TEST_STATUS,
                TEST_VERSION,
                LocalDateTime.now(),
                statusValidator
            );

            AccountEntity result = mapper.toEntity(domain);

            assertEquals("999", result.getAccountId());
        }

        @Test
        @DisplayName("Extracts amount from Money object")
        void extractsAmountFromMoney() {
            BigDecimal amount = new BigDecimal("7890.12");
            Account domain = new Account(
                TEST_ACCOUNT_ID,
                TEST_HOLDER_NAME,
                new Money(amount),
                TEST_STATUS,
                TEST_VERSION,
                LocalDateTime.now(),
                statusValidator
            );

            AccountEntity result = mapper.toEntity(domain);

            assertEquals(amount, result.getCashBalance());
        }
    }

    @Nested
    @DisplayName("Bidirectional mapping")
    class BidirectionalMappingTests {

        @Test
        @DisplayName("Entity to Domain to Entity preserves data")
        void entityToDomainToEntityPreservesData() {
            LocalDateTime now = LocalDateTime.now();
            AccountEntity original = new AccountEntity(
                TEST_ACCOUNT_ID.toString(),
                TEST_HOLDER_NAME,
                TEST_BALANCE,
                TEST_STATUS,
                TEST_VERSION,
                now
            );
            original.setId(1L);

            Account domain = mapper.toDomain(original);
            AccountEntity result = mapper.toEntity(domain);

            assertEquals(original.getAccountId(), result.getAccountId());
            assertEquals(original.getHolderName(), result.getHolderName());
            assertEquals(original.getCashBalance(), result.getCashBalance());
            assertEquals(original.getStatus(), result.getStatus());
            assertEquals(original.getVersion(), result.getVersion());
        }
    }
}
