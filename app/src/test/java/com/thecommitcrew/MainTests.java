package com.thecommitcrew;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.thecommitcrew.persistence.repository.InstrumentRepository;

import com.thecommitcrew.persistence.mapper.AccountMapper;
import com.thecommitcrew.persistence.mapper.InstrumentMapper;
import com.thecommitcrew.persistence.mapper.OrderMapper;
import com.thecommitcrew.persistence.mapper.PositionMapper;
import com.thecommitcrew.persistence.repository.AccountRepository;
import com.thecommitcrew.messaging.OrderEventPublisher;
import com.thecommitcrew.application.OrderSettlementService;
import com.thecommitcrew.messaging.ExecutionListener;
import com.thecommitcrew.messaging.PendingOrderRepublisher;

@SpringBootTest
@ActiveProfiles("h2")
class MainTests {

  @MockitoBean
  private InstrumentRepository instrumentRepository;
  
  @MockitoBean
  private AccountRepository accountRepository;
  
  @MockitoBean
  private OrderMapper orderMapper;
  
  @MockitoBean
  private PositionMapper positionMapper;
  
  @MockitoBean
  private AccountMapper accountMapper;
  
  @MockitoBean
  private InstrumentMapper instrumentMapper;

  @MockitoBean
  private OrderEventPublisher orderEventPublisher;

  @MockitoBean
  private OrderSettlementService orderSettlementService;

  @MockitoBean
  private ExecutionListener executionListener;

  @MockitoBean
  private PendingOrderRepublisher pendingOrderRepublisher;

  @Test
  @DisplayName("Application context loads without errors")
  void contextLoads() {
  }
}