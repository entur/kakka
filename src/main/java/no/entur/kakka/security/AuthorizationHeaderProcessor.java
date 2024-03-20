package no.entur.kakka.security;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.hc.core5.http.HttpHeaders;
import org.entur.oauth2.TokenService;
import org.springframework.stereotype.Component;

/**
 * Processor that sets a bearer token in the HTTP Authorization header.
 */
@Component("authorizationHeaderProcessor")
public class AuthorizationHeaderProcessor implements Processor {

    private final TokenService tokenService;

    public AuthorizationHeaderProcessor(TokenService tokenService) {
        this.tokenService = tokenService;
    }

    @Override
    public void process(Exchange exchange) {
        exchange.getIn().setHeader(HttpHeaders.AUTHORIZATION, "Bearer " + tokenService.getToken());
    }
}
