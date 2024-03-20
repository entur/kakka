package no.entur.kakka.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.stereotype.Component;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;

import static org.springframework.security.config.Customizer.withDefaults;

/**
 * Authentication and authorization configuration for Kakka.
 * All requests must be authenticated except for the OpenAPI and Actuator endpoints.
 */
@Profile("!test")
@EnableWebSecurity
@Component
public class KakkaWebSecurityConfigurerAdapter{

    @Bean
    CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedHeaders(Arrays.asList("Origin", "Accept", "X-Requested-With", "Content-Type", "Access-Control-Request-Method", "Access-Control-Request-Headers", "Authorization", "x-correlation-id"));
        configuration.addAllowedOrigin("*");
        configuration.setAllowedMethods(Arrays.asList("GET", "PUT", "POST", "DELETE"));
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, MultiIssuerAuthenticationManagerResolver multiIssuerAuthenticationManagerResolver) throws Exception {
        http.cors(withDefaults())
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeRequests(authz -> authz
                        .requestMatchers(AntPathRequestMatcher.antMatcher("/services/openapi.json")).permitAll()
                        .requestMatchers(AntPathRequestMatcher.antMatcher("/services/geocoder_admin/openapi.json")).permitAll()
                        .requestMatchers(AntPathRequestMatcher.antMatcher("/services/organisation_admin/openapi.json")).permitAll()
                        .requestMatchers(AntPathRequestMatcher.antMatcher("/services/export/openapi.json")).permitAll()
                        .requestMatchers(AntPathRequestMatcher.antMatcher("/services/osmpoifilter")).permitAll()
                        .requestMatchers(AntPathRequestMatcher.antMatcher("/actuator/prometheus")).permitAll()
                        .requestMatchers(AntPathRequestMatcher.antMatcher("/actuator/health")).permitAll()
                        .requestMatchers(AntPathRequestMatcher.antMatcher("/actuator/health/liveness")).permitAll()
                        .requestMatchers(AntPathRequestMatcher.antMatcher("/actuator/health/readiness")).permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(configurer -> configurer.authenticationManagerResolver(multiIssuerAuthenticationManagerResolver))
                .oauth2Client(withDefaults());


        return http.build();

    }

}
