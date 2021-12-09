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

import org.entur.oauth2.JwtRoleAssignmentExtractor;
import org.entur.oauth2.OAuth2TokenService;
import org.entur.oauth2.TokenService;
import org.rutebanken.helper.organisation.RoleAssignmentExtractor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.security.oauth2.client.OAuth2ClientProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configure Spring Beans for OAuth2 resource server and OAuth2 client security.
 */
@Configuration
public class OAuth2Config {

    @Bean
    TokenService tokenService(OAuth2ClientProperties properties, @Value("${kakka.oauth2.client.audience}") String audience) {
        return new OAuth2TokenService.Builder()
                .withOAuth2ClientProperties(properties)
                .withAudience(audience)
                .withClientRegistrationId("kakka")
                .build();
    }

    /**
     * Extract role assignments from a JWT token.
     *
     * @return role
     */
    @Bean
    public RoleAssignmentExtractor roleAssignmentExtractor() {
        return new JwtRoleAssignmentExtractor();
    }


}

