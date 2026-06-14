package com.jpmc.midascore.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Default security configuration when {@code midas.security.enabled} is false or absent.
 *
 * <p>Permits all requests so the Forage {@code /balance} flow and local development work with
 * no identity provider. Defining a {@link SecurityFilterChain} bean also switches off Spring
 * Boot's default "secure everything + generated password" behaviour. Method security is NOT
 * enabled here, so {@code @PreAuthorize} annotations are inert until the secured profile is on.
 */
@Configuration
@ConditionalOnProperty(prefix = "midas.security", name = "enabled", havingValue = "false", matchIfMissing = true)
public class NoSecurityConfig {

    @Bean
    public SecurityFilterChain permitAllFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        return http.build();
    }
}
