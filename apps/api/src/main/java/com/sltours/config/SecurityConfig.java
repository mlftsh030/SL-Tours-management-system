package com.sltours.config;

import com.sltours.accounts.repository.AccountRepository;
import com.sltours.accounts.service.AccountService;
import com.sltours.accounts.service.IdleSessionFilter;
import com.sltours.accounts.service.LoginRateLimitFilter;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.session.SessionRegistryImpl;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.context.SecurityContextHolderFilter;
import org.springframework.security.web.session.HttpSessionEventPublisher;
import org.springframework.web.cors.*;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {
    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, AccountService accounts, SessionRegistry sessions,
            @Value("${sltours.security.idle-timeout:30m}") Duration idleTimeout) throws Exception {
        http.cors(Customizer.withDefaults())
            .csrf(Customizer.withDefaults())
            .requestCache(cache -> cache.disable())
            .sessionManagement(session -> session
                .sessionFixation(fixation -> fixation.changeSessionId())
                .maximumSessions(5).sessionRegistry(sessions)
                .expiredSessionStrategy(event -> {
                    event.getResponse().setStatus(401);
                    event.getResponse().setContentType("application/json");
                    event.getResponse().getWriter().write("{\"message\":\"Your session has expired. Please sign in again.\"}");
                }))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/error", "/actuator/health", "/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                .requestMatchers("/api/v1/auth/csrf", "/api/v1/auth/me", "/api/v1/auth/login", "/api/v1/auth/register").permitAll()
                .requestMatchers(org.springframework.http.HttpMethod.POST, "/api/v1/auth/verify-email", "/api/v1/auth/password-reset/request",
                    "/api/v1/auth/password-reset/confirm").permitAll()
                .requestMatchers("/api/v1/admin/**").hasAnyRole("STAFF", "ADMIN")
                .requestMatchers("/api/v1/driver/**").hasRole("DRIVER")
                // Partners see only their own records; applications and referral-code lookups are public.
                .requestMatchers("/api/v1/partner/**").hasRole("PARTNER")
                .requestMatchers(org.springframework.http.HttpMethod.POST, "/api/v1/partner-applications").permitAll()
                .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/v1/partners/referrals/*").permitAll()
                .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/v1/public/**", "/api/v1/private-trips/**", "/api/v1/activities", "/api/v1/activities/*",
                    "/api/v1/seo/**").permitAll()
                .requestMatchers(org.springframework.http.HttpMethod.POST, "/api/v1/itineraries").permitAll()
                // Planning assistant: public, CSRF-protected, conversations bound to the visitor's session.
                .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/v1/assistant/status").permitAll()
                .requestMatchers(org.springframework.http.HttpMethod.POST, "/api/v1/assistant/conversations", "/api/v1/assistant/conversations/*/messages",
                    "/api/v1/assistant/conversations/*/enquiry").permitAll()
                .requestMatchers(org.springframework.http.HttpMethod.DELETE, "/api/v1/assistant/conversations/*").permitAll()
                .requestMatchers(org.springframework.http.HttpMethod.POST, "/api/v1/private-trips/requests", "/api/v1/private-trips/quote-requests").permitAll()
                .requestMatchers("/api/v1/account/**").hasRole("CUSTOMER")
                .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/v1/tours", "/api/v1/tours/*").permitAll()
                .requestMatchers(org.springframework.http.HttpMethod.POST, "/api/v1/bookings", "/api/v1/trip-planning-requests",
                    "/api/v1/enquiries").permitAll()
                .anyRequest().denyAll())
            .exceptionHandling(errors -> errors
                .authenticationEntryPoint((request, response, exception) -> {
                    response.setStatus(401); response.setContentType("application/json");
                    response.getWriter().write("{\"message\":\"Please sign in to continue\"}");
                })
                .accessDeniedHandler((request, response, exception) -> {
                    response.setStatus(403); response.setContentType("application/json");
                    response.getWriter().write("{\"message\":\"Access denied or security token expired. Refresh and try again.\"}");
                }))
            .formLogin(login -> login.loginProcessingUrl("/api/v1/auth/login")
                .successHandler((request, response, authentication) -> {
                    accounts.recordLogin(authentication.getName());
                    IdleSessionFilter.markActive(request, Clock.systemUTC()); response.setStatus(204);
                })
                .failureHandler((request, response, exception) -> {
                    response.setStatus(401); response.setContentType("application/json");
                    response.getWriter().write("{\"message\":\"Email or password is incorrect\"}");
                }).permitAll())
            .logout(logout -> logout.logoutUrl("/api/v1/auth/logout")
                .deleteCookies("JSESSIONID")
                .logoutSuccessHandler((request, response, authentication) -> response.setStatus(204)))
            .addFilterBefore(new LoginRateLimitFilter(), UsernamePasswordAuthenticationFilter.class)
            .addFilterAfter(new IdleSessionFilter(idleTimeout), SecurityContextHolderFilter.class);
        return http.build();
    }

    @Bean
    UserDetailsService userDetailsService(AccountRepository accounts) {
        return email -> accounts.findByEmail(AccountService.normalizeEmail(email))
            .map(account -> User.withUsername(account.getEmail()).password(account.getPasswordHash())
                .roles(account.getRole().name()).disabled(!account.isEnabled()).build())
            .orElseThrow(() -> new UsernameNotFoundException("Invalid credentials"));
    }
    @Bean PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(12); }
    @Bean SessionRegistry sessionRegistry() { return new SessionRegistryImpl(); }
    @Bean HttpSessionEventPublisher httpSessionEventPublisher() { return new HttpSessionEventPublisher(); }

    @Bean
    CorsConfigurationSource corsConfigurationSource(@Value("${sltours.cors.allowed-origins}") String origins) {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(List.of(origins.split(",")));
        configuration.setAllowedMethods(List.of("GET", "POST", "PATCH", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Content-Type", "Accept", "X-CSRF-TOKEN", IdleSessionFilter.BACKGROUND_HEADER));
        configuration.setExposedHeaders(List.of("Location"));
        configuration.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
