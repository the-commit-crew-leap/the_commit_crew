package com.thecommitcrew.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;
import com.thecommitcrew.auth.JwtAuthenticationFilter;
import com.thecommitcrew.auth.AuthServiceClient;

@Configuration
public class SecurityConfig {
    
    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }

    @Bean
    public JwtAuthenticationFilter jwtAuthenticationFilter(AuthServiceClient authServiceClient) {
        return new JwtAuthenticationFilter(authServiceClient);
    }
}