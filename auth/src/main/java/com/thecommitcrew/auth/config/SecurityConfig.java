package com.thecommitcrew.auth.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import com.thecommitcrew.auth.AuthEntryPointJwt;
import com.thecommitcrew.auth.JwtAuthenticationFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {
    private static final Logger logger = LoggerFactory.getLogger(SecurityConfig.class);

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final AuthEntryPointJwt authEntryPointJwt;
    
    @Value("${auth.enabled:true}")
    private boolean authEnabled;

    public SecurityConfig(
            JwtAuthenticationFilter jwtAuthenticationFilter,
            AuthEntryPointJwt authEntryPointJwt) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.authEntryPointJwt = authEntryPointJwt;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        logger.info("SecurityConfig: authEnabled = {}", authEnabled);
        var authConfig = http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS));

        if (authEnabled) {
            authConfig
                .exceptionHandling(ex -> ex.authenticationEntryPoint(authEntryPointJwt))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/auth/**", "/actuator/health").permitAll()
                        .anyRequest().authenticated()
                )
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        } else {
            authConfig.authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        }

        return authConfig.build();
    }
}