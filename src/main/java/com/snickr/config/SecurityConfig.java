package com.snickr.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    /**
     * Manage Project Access Permissions, Login, and Logout Logic
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) {
        http
                // Authorization config
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/css/**", "/js/**", "/images/**").permitAll()
                        .requestMatchers("/", "/register").permitAll()
                        .anyRequest().authenticated()
                )
                // Login config
                .formLogin(form -> form
                        .loginPage("/login")
                        .loginProcessingUrl("/login-process")
                        .defaultSuccessUrl("/dashboard", true)
                        .permitAll()
                )
                // Logout config
                .logout(logout -> logout
                        .logoutSuccessUrl("/login?logout")
                        .permitAll()
                );

        return http.build();
    }
}