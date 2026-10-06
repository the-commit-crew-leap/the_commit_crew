package com.thecommitcrew.application;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@DisplayName("AfterCommit Tests")
@ExtendWith(MockitoExtension.class)
class AfterCommitTest {

    private Runnable mockAction;

    @BeforeEach
    void setUp() {
        mockAction = mock(Runnable.class);
    }

    @Nested
    @DisplayName("When transaction is active")
    class WhenTransactionIsActive {

        @SuppressWarnings("null")
        @Test
        @DisplayName("Should defer action execution until after commit")
        void shouldDeferActionExecution() {
            try (MockedStatic<TransactionSynchronizationManager> mocked = 
                    Mockito.mockStatic(TransactionSynchronizationManager.class)) {

                // Arrange
                mocked.when(TransactionSynchronizationManager::isSynchronizationActive)
                    .thenReturn(true);

                ArgumentCaptor<TransactionSynchronization> captor = 
                    ArgumentCaptor.forClass(TransactionSynchronization.class);

                // Act
                AfterCommit.run(mockAction);

                // Assert
                verify(mockAction, never()).run();
                mocked.verify(() -> TransactionSynchronizationManager.registerSynchronization(captor.capture()));
                
                // Verify that afterCommit method triggers the action
                captor.getValue().afterCommit();
                verify(mockAction, times(1)).run();
            }
        }

        @SuppressWarnings("null")
        @Test
        @DisplayName("Should register synchronization with TransactionSynchronizationManager")
        void shouldRegisterSynchronization() {
            try (MockedStatic<TransactionSynchronizationManager> mocked = 
                    Mockito.mockStatic(TransactionSynchronizationManager.class)) {

                // Arrange
                mocked.when(TransactionSynchronizationManager::isSynchronizationActive)
                    .thenReturn(true);

                // Act
                AfterCommit.run(mockAction);

                // Assert
                mocked.verify(() -> TransactionSynchronizationManager.registerSynchronization(any()), 
                    times(1));
            }
        }

        @SuppressWarnings("null")
        @Test
        @DisplayName("Should execute action when afterCommit is called on registered synchronization")
        void shouldExecuteActionOnAfterCommit() {
            try (MockedStatic<TransactionSynchronizationManager> mocked = 
                    Mockito.mockStatic(TransactionSynchronizationManager.class)) {

                // Arrange
                mocked.when(TransactionSynchronizationManager::isSynchronizationActive)
                    .thenReturn(true);

                ArgumentCaptor<TransactionSynchronization> captor = 
                    ArgumentCaptor.forClass(TransactionSynchronization.class);

                // Act
                AfterCommit.run(mockAction);
                
                // Assert - verify with captor BEFORE using captor.getValue()
                mocked.verify(() -> TransactionSynchronizationManager.registerSynchronization(captor.capture()));
                captor.getValue().afterCommit();
                verify(mockAction, times(1)).run();
            }
        }

        @SuppressWarnings("null")
        @Test
        @DisplayName("Should handle action that throws exception")
        void shouldHandleActionException() {
            try (MockedStatic<TransactionSynchronizationManager> mocked = 
                    Mockito.mockStatic(TransactionSynchronizationManager.class)) {

                // Arrange
                mocked.when(TransactionSynchronizationManager::isSynchronizationActive)
                    .thenReturn(true);
                RuntimeException testException = new RuntimeException("Test error");
                doThrow(testException).when(mockAction).run();

                ArgumentCaptor<TransactionSynchronization> captor = 
                    ArgumentCaptor.forClass(TransactionSynchronization.class);

                // Act
                AfterCommit.run(mockAction);
                
                // Verify with captor
                mocked.verify(() -> TransactionSynchronizationManager.registerSynchronization(captor.capture()));
                
                // Assert - exception propagates from afterCommit
                TransactionSynchronization sync = captor.getValue();
                RuntimeException thrown = assertThrows(RuntimeException.class, sync::afterCommit);
                assertTrue(thrown.getMessage().contains("Test error"));
                verify(mockAction, times(1)).run();
            }
        }
    }

    @Nested
    @DisplayName("When no transaction is active")
    class WhenNoTransactionIsActive {

        @SuppressWarnings("null")
        @Test
        @DisplayName("Should execute action immediately")
        void shouldExecuteActionImmediately() {
            try (MockedStatic<TransactionSynchronizationManager> mocked = 
                    Mockito.mockStatic(TransactionSynchronizationManager.class)) {

                // Arrange
                mocked.when(TransactionSynchronizationManager::isSynchronizationActive)
                    .thenReturn(false);

                // Act
                AfterCommit.run(mockAction);

                // Assert
                verify(mockAction, times(1)).run();
                mocked.verify(() -> TransactionSynchronizationManager.registerSynchronization(any()), 
                    never());
            }
        }

        @SuppressWarnings("null")
        @Test
        @DisplayName("Should not register synchronization")
        void shouldNotRegisterSynchronization() {
            try (MockedStatic<TransactionSynchronizationManager> mocked = 
                    Mockito.mockStatic(TransactionSynchronizationManager.class)) {

                // Arrange
                mocked.when(TransactionSynchronizationManager::isSynchronizationActive)
                    .thenReturn(false);

                // Act
                AfterCommit.run(mockAction);

                // Assert
                mocked.verify(() -> TransactionSynchronizationManager.registerSynchronization(any()), 
                    never());
            }
        }

        @Test
        @DisplayName("Should handle action that throws exception")
        void shouldHandleActionException() {
            try (MockedStatic<TransactionSynchronizationManager> mocked = 
                    Mockito.mockStatic(TransactionSynchronizationManager.class)) {

                // Arrange
                mocked.when(TransactionSynchronizationManager::isSynchronizationActive)
                    .thenReturn(false);
                RuntimeException testException = new RuntimeException("Test error");
                doThrow(testException).when(mockAction).run();

                // Act & Assert
                try {
                    AfterCommit.run(mockAction);
                } catch (RuntimeException e) {
                    assertTrue(e.getMessage().contains("Test error"));
                }
                verify(mockAction, times(1)).run();
            }
        }
    }

    @Nested
    @DisplayName("Action execution behavior")
    class ActionExecutionBehavior {

        @SuppressWarnings("null")
        @Test
        @DisplayName("Should execute action exactly once per AfterCommit.run call with active transaction")
        void shouldExecuteActionOnceWithTransaction() {
            try (MockedStatic<TransactionSynchronizationManager> mocked = 
                    Mockito.mockStatic(TransactionSynchronizationManager.class)) {

                // Arrange
                mocked.when(TransactionSynchronizationManager::isSynchronizationActive)
                    .thenReturn(true);

                ArgumentCaptor<TransactionSynchronization> captor = 
                    ArgumentCaptor.forClass(TransactionSynchronization.class);

                // Act
                AfterCommit.run(mockAction);
                
                // Assert - verify with captor BEFORE using captor.getValue()
                mocked.verify(() -> TransactionSynchronizationManager.registerSynchronization(captor.capture()));
                captor.getValue().afterCommit();
                verify(mockAction, times(1)).run();
            }
        }

        @SuppressWarnings("null")
        @Test
        @DisplayName("Should allow multiple AfterCommit.run calls with different actions")
        void shouldAllowMultipleActionsWithActiveTransaction() {
            try (MockedStatic<TransactionSynchronizationManager> mocked = 
                    Mockito.mockStatic(TransactionSynchronizationManager.class)) {

                // Arrange
                mocked.when(TransactionSynchronizationManager::isSynchronizationActive)
                    .thenReturn(true);

                Runnable action1 = mock(Runnable.class);
                Runnable action2 = mock(Runnable.class);

                // Act
                AfterCommit.run(action1);
                AfterCommit.run(action2);

                // Assert - verify was called twice total (not once per action)
                mocked.verify(() -> TransactionSynchronizationManager.registerSynchronization(any()), times(2));
            }
        }

        @Test
        @DisplayName("Should support lambda expressions as actions")
        void shouldSupportLambdaExpressions() {
            try (MockedStatic<TransactionSynchronizationManager> mocked = 
                    Mockito.mockStatic(TransactionSynchronizationManager.class)) {

                // Arrange
                mocked.when(TransactionSynchronizationManager::isSynchronizationActive)
                    .thenReturn(false);

                AtomicBoolean executed = new AtomicBoolean(false);

                // Act
                AfterCommit.run(() -> executed.set(true));

                // Assert
                assertTrue(executed.get());
            }
        }
    }
}