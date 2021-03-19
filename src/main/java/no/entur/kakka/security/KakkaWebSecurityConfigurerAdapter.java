package no.entur.kakka.security;

import org.entur.oauth2.MultiIssuerAuthenticationManagerResolver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.stereotype.Component;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;

import static org.springframework.security.config.Customizer.withDefaults;

/**
 * Authentication and authorization configuration for Kakka.
 * All requests must be authenticated except for the Swagger and Actuator endpoints.
 * The Oauth2 ID-provider (Keycloak or Auth0) is identified thanks to {@link MultiIssuerAuthenticationManagerResolver}.
 */
@Profile("!test")
@EnableWebSecurity
@EnableGlobalMethodSecurity(prePostEnabled = true)
@Component
public class KakkaWebSecurityConfigurerAdapter extends WebSecurityConfigurerAdapter {

    private final MultiIssuerAuthenticationManagerResolver multiIssuerAuthenticationManagerResolver;

    public KakkaWebSecurityConfigurerAdapter(MultiIssuerAuthenticationManagerResolver multiIssuerAuthenticationManagerResolver) {
        this.multiIssuerAuthenticationManagerResolver = multiIssuerAuthenticationManagerResolver;
    }

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

    @Override
    public void configure(HttpSecurity http) throws Exception {
        http.cors(withDefaults())
                .csrf().disable()
                .authorizeRequests()
                .antMatchers("/services/swagger.json").permitAll()
                .antMatchers("/services/geocoder_admin/swagger.json").permitAll()
                .antMatchers("/services/organisation_admin/swagger.json").permitAll()
                .antMatchers("/services/export/swagger.json").permitAll()
                // exposed internally only, on a different port (pod-level)
                .antMatchers("/actuator/prometheus").permitAll()
                .antMatchers("/actuator/health").permitAll()
                .antMatchers("/actuator/health/liveness").permitAll()
                .antMatchers("/actuator/health/readiness").permitAll()
                .anyRequest().authenticated()
                .and()
                .oauth2ResourceServer().authenticationManagerResolver(this.multiIssuerAuthenticationManagerResolver)
                .and()
                .oauth2Client();

    }

}
