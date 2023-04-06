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

package no.entur.kakka.routes.status;

import no.entur.kakka.KakkaRouteBuilderIntegrationTestBase;
import no.entur.kakka.TestApp;
import org.apache.camel.EndpointInject;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.AdviceWith;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, classes = TestApp.class)
public class StatusRouteIntegrationTest extends KakkaRouteBuilderIntegrationTestBase {


    @Produce("direct:updateStatus")
    protected ProducerTemplate updateStatus;

    @EndpointInject("mock:JobEventQueue")
    protected MockEndpoint jobEventQueue;

    @Test
    public void testJobEventQueue() throws Exception {

        AdviceWith.adviceWith(context,"update-status", a -> a.weaveByToUri("google-pubsub:(.*):JobEventQueue").replace().to("mock:JobEventQueue") );

        context.start();
        updateStatus.sendBody(status().toString());

        jobEventQueue.expectedMessageCount(1);
        final String body = jobEventQueue.getExchanges().get(0).getIn().getBody(String.class);
        Assertions.assertFalse(body.isEmpty());
        Assertions.assertTrue(body.contains("EXPORT"));
    }

    private JobEvent status() {
        return JobEvent.builder().jobDomain(JobEvent.JobDomain.TIAMAT).action("EXPORT").correlationId("corrId").state(JobEvent.State.STARTED).build();
    }
}
