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

import no.entur.kakka.Constants;
import org.apache.camel.CamelContext;
import org.apache.camel.component.http.HttpClientConfigurer;
import org.apache.camel.component.http.HttpComponent;
import org.apache.hc.core5.http.message.BasicHeader;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Arrays;

@Configuration
public class HttpClientConfig {

    @Value("${http.client.name:kakka}")
    private String clientName;

    @Value("${HOSTNAME:kakka}")
    private String clientId;

    @Bean
    public HttpClientConfigurer httpClientConfigurer(@Autowired CamelContext camelContext) {
        HttpComponent httpComponent = camelContext.getComponent("http", HttpComponent.class);
        HttpClientConfigurer httpClientConfigurer = httpClientBuilder -> httpClientBuilder.setDefaultHeaders(
                Arrays.asList(new BasicHeader(Constants.ET_CLIENT_ID_HEADER, clientId), new BasicHeader(Constants.ET_CLIENT_NAME_HEADER, clientName))
        );

        httpComponent.setHttpClientConfigurer(httpClientConfigurer);
        return httpClientConfigurer;
    }

}
