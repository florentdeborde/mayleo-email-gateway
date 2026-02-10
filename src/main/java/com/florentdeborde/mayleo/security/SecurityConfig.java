package com.florentdeborde.mayleo.security;

import com.florentdeborde.mayleo.repository.ApiClientRepository;
import com.florentdeborde.mayleo.service.HmacService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Value("${app.security.key-salt}")
    private String salt;

    @Value("${app.security.flag-hmac-enabled}")
    private boolean isHmacEnabled;

    @Bean
    public UserDetailsService userDetailsService() {
        // Define an empty user details manager to disable the automatic
        // generation of the default security password
        return new InMemoryUserDetailsManager();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, ApiClientRepository repo, SecurityRegistry securityRegistry, HmacService hmacService) throws Exception {
        http
                // Manage CORS
                .cors(cors -> cors.configurationSource(request -> {
                    var config = new org.springframework.web.cors.CorsConfiguration();
                    // Safe: Global CORS is permissive to allow Gateway interoperability;
                    // strict origin validation is enforced per API Key in SecurityFilter.
                    config.setAllowedOriginPatterns(java.util.List.of("*"));
                    config.setAllowedMethods(java.util.List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
                    config.setAllowedHeaders(java.util.List.of("*"));
                    config.setAllowCredentials(true);
                    return config;
                }))

                // Disable CSRF as the API is stateless and uses API Keys
                .csrf(AbstractHttpConfigurer::disable)

                // Configure endpoint permissions
                .authorizeHttpRequests(auth -> auth
                        // Allow all public routes
                        .requestMatchers(securityRegistry.getPublicRoutes()).permitAll()
                        // All other requests must be authenticated via our custom filter
                        .anyRequest().authenticated()
                )

                // Register our custom SecurityFilter before the standard authentication filter
                .addFilterBefore(new SecurityFilter(repo, securityRegistry, hmacService, salt, isHmacEnabled), UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}