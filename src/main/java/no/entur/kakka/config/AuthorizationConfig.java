/*
 * Licensed under the EUPL, Version 1.2 or – as soon they will be approved by
 * the European Commission - subsequent versions of the EUPL (the "Licence");
 * You may not use this work except in compliance with the Licence.
 * You may obtain a copy of the Licence at:
 *
 *   https://joinup.ec.europa.eu/software/page/eupl
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the Licence is distributed on an "AS IS" basis,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the Licence for the specific language governing permissions and
 * limitations under the Licence.
 *
 */

package no.entur.kakka.config;

import no.entur.kakka.security.KakkaAuthorizationService;
import no.entur.kakka.security.DefaultKakkaAuthorizationService;
import org.entur.oauth2.AuthorizedWebClientBuilder;
import org.entur.oauth2.JwtRoleAssignmentExtractor;
import org.entur.ror.permission.RemoteBabaRoleAssignmentExtractor;
import org.rutebanken.helper.organisation.RoleAssignmentExtractor;
import org.rutebanken.helper.organisation.authorization.AuthorizationService;
import org.rutebanken.helper.organisation.authorization.DefaultAuthorizationService;
import org.rutebanken.helper.organisation.authorization.FullAccessAuthorizationService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.security.oauth2.client.OAuth2ClientProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Configure authorization.
 */
@Configuration
public class AuthorizationConfig {

    @ConditionalOnProperty(
            value = "kakka.security.role.assignment.extractor",
            havingValue = "jwt",
            matchIfMissing = true
    )
    @Bean
    public RoleAssignmentExtractor jwtRoleAssignmentExtractor() {
        return new JwtRoleAssignmentExtractor();
    }

    @ConditionalOnProperty(
            value = "kakka.security.role.assignment.extractor",
            havingValue = "baba"
    )
    @Bean
    public RoleAssignmentExtractor babaRoleAssignmentExtractor(
            WebClient internalWebClient,
            @Value("${user.permission.rest.service.url}") String url
    ) {
        return new RemoteBabaRoleAssignmentExtractor(internalWebClient, url);
    }

    /**
     * Return a WebClient for authorized API calls.
     * The WebClient inserts a JWT bearer token in the Authorization HTTP header.
     * The JWT token is obtained from the configured Authorization Server.
     *
     * @param properties The spring.security.oauth2.client.registration.* properties
     * @param audience   The API audience, required for obtaining a token from Auth0
     * @return a WebClient for authorized API calls.
     */
    @ConditionalOnProperty(
            value = "kakka.security.role.assignment.extractor",
            havingValue = "baba"
    )
    @Bean
    WebClient internalWebClient(
            WebClient.Builder webClientBuilder,
            OAuth2ClientProperties properties,
            @Value("${kakka.oauth2.client.audience}") String audience
    ) {
        return new AuthorizedWebClientBuilder(webClientBuilder)
                .withOAuth2ClientProperties(properties)
                .withAudience(audience)
                .withClientRegistrationId("kakka")
                .build();
    }


    @ConditionalOnProperty(
            value = "kakka.security.authorization-service",
            havingValue = "token-based"
    )
    @Bean("authorizationService")
    public AuthorizationService<Long> tokenBasedAuthorizationService(RoleAssignmentExtractor roleAssignmentExtractor) {
        return new DefaultAuthorizationService<>(roleAssignmentExtractor);
    }

    @ConditionalOnProperty(
            value = "kakka.security.authorization-service",
            havingValue = "full-access"
    )
    @Bean("authorizationService")
    public AuthorizationService<Long> fullAccessAuthorizationService() {
        return new FullAccessAuthorizationService();
    }


    @Bean
    public KakkaAuthorizationService kakkaAuthorizationService(AuthorizationService<Long> authorizationService) {
        return new DefaultKakkaAuthorizationService(authorizationService);
    }

}


