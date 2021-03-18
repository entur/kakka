package no.entur.kakka.security;

/**
 * Retrieve an OAuth2 bearer token from an Authorization Server.
 */
public interface TokenService {

    String getToken();
}
