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

import com.auth0.client.auth.AuthAPI;
import com.auth0.exception.Auth0Exception;
import com.auth0.json.auth.TokenHolder;
import com.auth0.net.AuthRequest;
import no.entur.kakka.exceptions.KakkaException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Profile("auth0")
public class Auth0TokenService implements TokenService {

    @Autowired
    private AuthAPI authAPI;

    @Value("${auth0.audience}")
    private String audience;


    @Override
    public String getToken() {
        AuthRequest request = authAPI.requestToken(audience);
        try {
            TokenHolder holder = request.execute();
            return holder.getAccessToken();
        } catch (Auth0Exception e) {
            throw new KakkaException("Could not retrieve OAuth2 token", e);
        }
    }

}
