package com.thecommitcrew.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.client.RestTemplate;
import com.thecommitcrew.auth.JwtAuthenticationFilter;
import com.thecommitcrew.auth.AuthServiceClient;

@Configuration
@EnableWebSecurity
public class SecurityConfig {
    
    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }

    @Bean
    public JwtAuthenticationFilter jwtAuthenticationFilter(AuthServiceClient authServiceClient) {
        return new JwtAuthenticationFilter(authServiceClient);
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, AuthServiceClient authServiceClient) throws Exception {
        http
            .authorizeHttpRequests(authz -> authz
                // Allow public access to Swagger/OpenAPI endpoints
                .requestMatchers(
                    "/swagger-ui/**",
                    "/v3/api-docs/**",
                    "/swagger-resources/**",
                    "/swagger-resources",
                    "/webjars/**"
                ).permitAll()
                // All other requests require authentication
                .anyRequest().authenticated()
            )
            .addFilterBefore(
                jwtAuthenticationFilter(authServiceClient),
                UsernamePasswordAuthenticationFilter.class
            );
        return http.build();
    }
}