package com.retailshop.config;

import com.retailshop.entity.StaffUser;
import com.retailshop.enums.AppPermission;
import com.retailshop.security.CustomerJwtFilter;
import com.retailshop.security.StaffJwtFilter;
import com.retailshop.service.StaffUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

@Configuration
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final StaffUserService staffUserService;
    private final StaffJwtFilter staffJwtFilter;
    private final CustomerJwtFilter customerJwtFilter;
    @Value("${app.cors.allowed-origins}")
    private String allowedOrigins;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(org.springframework.http.HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers(org.springframework.http.HttpMethod.GET,
                                "/",
                                "/index.html",
                                "/env-config.js",
                                "/favicon.ico",
                                "/assets/**",
                                "/login",
                                "/customer-login",
                                "/products",
                                "/cart",
                                "/wishlist",
                                "/checkout",
                                "/orders",
                                "/account",
                                "/privacy-policy",
                                "/app",
                                "/app/**").permitAll()
                        .requestMatchers(org.springframework.http.HttpMethod.HEAD,
                                "/",
                                "/index.html",
                                "/env-config.js",
                                "/favicon.ico",
                                "/assets/**",
                                "/login",
                                "/customer-login",
                                "/products",
                                "/cart",
                                "/wishlist",
                                "/checkout",
                                "/orders",
                                "/account",
                                "/privacy-policy",
                                "/app",
                                "/app/**").permitAll()
                        .requestMatchers("/actuator/health", "/api/auth/login", "/api/auth/send-otp", "/api/auth/verify-otp",
                                "/api/auth/google", "/api/auth/google/verify-mobile").permitAll()
                        .requestMatchers("/api/razorpay/webhook").permitAll()
                        .requestMatchers("/api/whatsapp/webhook", "/api/whatsapp/webhook/**").permitAll()
                        .requestMatchers("/api/omnichannel/webhooks/**").permitAll()
                        .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/images/**").permitAll()
                        .requestMatchers(org.springframework.http.HttpMethod.HEAD, "/api/images/**").permitAll()
                        .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/omnichannel/products/**").permitAll()
                        .requestMatchers(org.springframework.http.HttpMethod.POST, "/api/omnichannel/products/**", "/api/omnichannel/leads").permitAll()
                        .requestMatchers(org.springframework.http.HttpMethod.POST, "/api/campaign-leads/visits").permitAll()
                        .requestMatchers(org.springframework.http.HttpMethod.POST, "/api/site-interactions/visit").permitAll()
                        .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/settings/receipt").permitAll()
                        .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/product-categories/options").permitAll()
                        .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/products/catalog", "/api/products/catalog/home", "/api/products/catalog/trending").permitAll()
                        .requestMatchers("/api/cart/**", "/api/wishlist/**", "/api/address/**", "/api/order/**", "/api/orders/**", "/api/checkout/**", "/api/customer-profile/**").hasRole("CUSTOMER")
                        .anyRequest().authenticated())
                .addFilterBefore(staffJwtFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(customerJwtFilter, StaffJwtFilter.class)
                .httpBasic(httpBasic -> httpBasic.disable());

        return http.build();
    }

    @Bean
    public UserDetailsService userDetailsService() {
        return username -> {
            StaffUser user;
            try {
                user = staffUserService.getByUsername(username);
            } catch (RuntimeException ex) {
                throw new UsernameNotFoundException("User not found", ex);
            }
            String[] authorities = Stream.concat(
                            Stream.of("ROLE_" + user.getRole().name()),
                            staffUserService.getEffectivePermissions(user)
                                    .stream()
                                    .map(AppPermission::name)
                                    .map(permission -> "PERM_" + permission))
                    .toArray(String[]::new);

            return User.withUsername(user.getUsername())
                    .password(user.getPasswordHash())
                    .disabled(!Boolean.TRUE.equals(user.getEnabled()))
                    .authorities(authorities)
                    .build();
        };
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
        return configuration.getAuthenticationManager();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(Arrays.stream(allowedOrigins.split("\\s*,\\s*")).toList());
        configuration.setAllowedMethods(List.of("GET", "HEAD", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setExposedHeaders(List.of("Authorization"));

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
