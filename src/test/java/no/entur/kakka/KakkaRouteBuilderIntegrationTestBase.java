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

package no.entur.kakka;

import org.apache.camel.builder.AdviceWithRouteBuilder;
import org.apache.camel.model.ModelCamelContext;
import org.apache.camel.test.spring.CamelSpringBootRunner;
import org.apache.camel.test.spring.UseAdviceWith;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.gcp.pubsub.core.PubSubTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

@RunWith(CamelSpringBootRunner.class)
@ActiveProfiles({"default", "in-memory-blobstore", "google-pubsub-emulator"})
@UseAdviceWith
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
public abstract class KakkaRouteBuilderIntegrationTestBase {

    @Autowired
    protected ModelCamelContext context;

    @Autowired
    protected PubSubTemplate pubSubTemplate;


    protected void replaceEndpoint(String routeId, String originalEndpoint, String replacementEndpoint) throws Exception {

        AdviceWithRouteBuilder.adviceWith(context, routeId, a ->{
            a.interceptSendToEndpoint(originalEndpoint)
                    .skipSendToOriginalEndpoint().to(replacementEndpoint);
        });
    }


}
