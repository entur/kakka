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

import com.google.pubsub.v1.PubsubMessage;
import no.entur.kakka.KakkaRouteBuilderIntegrationTestBase;
import no.entur.kakka.TestApp;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, classes = TestApp.class)
public class StatusRouteIntegrationTest extends KakkaRouteBuilderIntegrationTestBase {


    @Produce("direct:updateStatus")
    protected ProducerTemplate updateStatus;

    @Test
    public void testJobEventQueue() throws Exception {

        context.start();
        updateStatus.sendBody(status().toString());

        List<PubsubMessage> messages = pubSubTemplate.pullAndAck(StatusRouteBuilder.JOB_EVENT_QUEUE, 1, false);
        Assertions.assertEquals(messages.size(), 1);
        PubsubMessage pubsubMessage = messages.get(0);
        Assertions.assertTrue(pubsubMessage.getData().size() > 0);
        String body = pubsubMessage.getData().toStringUtf8();
        Assertions.assertTrue(body.contains("EXPORT"));

    }

    private JobEvent status() {
        return JobEvent.builder().jobDomain(JobEvent.JobDomain.TIAMAT).action("EXPORT").correlationId("corrId").state(JobEvent.State.STARTED).build();
    }
}
