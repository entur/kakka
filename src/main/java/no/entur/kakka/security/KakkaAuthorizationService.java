package no.entur.kakka.security;

/**
 *  Service that verifies the privileges of the API clients.
 */
public interface KakkaAuthorizationService {


    /**
     * Verify that the user has full administrator privileges on route data.
     */
    void verifyRouteDataAdministratorPrivileges();

    /**
     * Verify that the user has full administrator privileges on organisations.
     */
    void verifyOrganisationAdministratorPrivileges();
}
