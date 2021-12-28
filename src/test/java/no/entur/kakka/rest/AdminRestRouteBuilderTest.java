package no.entur.kakka.rest;

import no.entur.kakka.KakkaRouteBuilderIntegrationTestBase;
import no.entur.kakka.TestApp;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;


@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT, classes = TestApp.class)
public class AdminRestRouteBuilderTest extends KakkaRouteBuilderIntegrationTestBase {

    @Produce("http:localhost:28081/services/osmpoifilter")
    protected ProducerTemplate getTemplate;

    @Test
    public void testGetPoiFilters() throws Exception {
        context.start();
        getTemplate.sendBody(null);
    }

    @TestConfiguration
    @EnableWebSecurity
    static class AdminRestRouteBuilderTestContextConfiguration extends WebSecurityConfigurerAdapter {

        @Override
        protected void configure(HttpSecurity http) throws Exception {
            http.csrf().disable()
                    .authorizeRequests(authorizeRequests ->
                            authorizeRequests
                                    .anyRequest().permitAll()
                    );

        }
    }
}
