package com.thecommitcrew.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(AlpacaProperties.class)
public class AlpacaConfig {
}
