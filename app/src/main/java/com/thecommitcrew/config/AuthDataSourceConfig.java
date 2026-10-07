package com.thecommitcrew.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

@Configuration
public class AuthDataSourceConfig {
    
    @Bean(name = "authDataSource")
    @ConfigurationProperties(prefix = "spring.auth-datasource")
    public DataSource authDataSource() {
        return DataSourceBuilder.create().build();
    }
}
