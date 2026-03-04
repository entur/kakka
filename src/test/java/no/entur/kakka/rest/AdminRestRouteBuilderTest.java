package no.entur.kakka.rest;

import no.entur.kakka.KakkaRouteBuilderIntegrationTestBase;
import no.entur.kakka.TestApp;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;


@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT, classes = TestApp.class)
public class AdminRestRouteBuilderTest extends KakkaRouteBuilderIntegrationTestBase {

    @Test
    public void testContextLoads() throws Exception {
        context.start();
    }

    @TestConfiguration
    @EnableWebSecurity
    static class AdminRestRouteBuilderTestContextConfiguration {

        @Bean
        public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
            http.csrf(AbstractHttpConfigurer::disable)
                    .authorizeHttpRequests(authorizeRequests ->
                            authorizeRequests
                                    .anyRequest().permitAll()
                    );
            return http.build();
        }

    }
}
