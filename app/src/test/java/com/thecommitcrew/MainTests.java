package com.thecommitcrew;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

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
import com.thecommitcrew.auth.JwtTokenProvider;

@SpringBootTest()
class MainTests {

  @MockBean
  private InstrumentRepository instrumentRepository;
  
  @MockBean
  private AccountRepository accountRepository;
  
  @MockBean
  private OrderMapper orderMapper;
  
  @MockBean
  private PositionMapper positionMapper;
  
  @MockBean
  private AccountMapper accountMapper;
  
  @MockBean
  private InstrumentMapper instrumentMapper;

  @MockBean
  private OrderEventPublisher orderEventPublisher;

  @MockBean
  private OrderSettlementService orderSettlementService;

  @MockBean
  private ExecutionListener executionListener;

  @MockBean
  private PendingOrderRepublisher pendingOrderRepublisher;

  @MockBean
  private JwtTokenProvider jwtTokenProvider;

  @Test
  void contextLoads() {
  }

}