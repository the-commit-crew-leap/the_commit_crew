package com.thecommitcrew.auth.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@TestPropertySource(properties = "auth.enabled=true")
class SecurityConfigTest {

    @Autowired
    private SecurityFilterChain securityFilterChain;

    @Test
    void securityFilterChainBeanExists() {
        assertThat(securityFilterChain).isNotNull();
    }

    @Test
    void contextLoadsWithAuthEnabled() {
        assertThat(securityFilterChain).isNotNull();
    }
}