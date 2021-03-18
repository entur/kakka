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

package no.entur.kakka.security;

import org.springframework.security.oauth2.client.AuthorizedClientServiceReactiveOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizeRequest;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.stereotype.Service;

@Service
public class OAuth2TokenService implements TokenService {

    private final AuthorizedClientServiceReactiveOAuth2AuthorizedClientManager authorizedClientServiceReactiveOAuth2AuthorizedClientManager;

    public OAuth2TokenService(AuthorizedClientServiceReactiveOAuth2AuthorizedClientManager authorizedClientServiceReactiveOAuth2AuthorizedClientManager) {
        this.authorizedClientServiceReactiveOAuth2AuthorizedClientManager = authorizedClientServiceReactiveOAuth2AuthorizedClientManager;
    }

    @Override
    public String getToken() {
        OAuth2AuthorizeRequest oAuth2AuthorizeRequest = OAuth2AuthorizeRequest.withClientRegistrationId("kakka").principal("kakka").build();
        OAuth2AuthorizedClient authorizedClient = authorizedClientServiceReactiveOAuth2AuthorizedClientManager.authorize(oAuth2AuthorizeRequest).block();
        return authorizedClient.getAccessToken().getTokenValue();
    }


}
