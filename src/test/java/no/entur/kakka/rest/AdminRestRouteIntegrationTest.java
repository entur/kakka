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

package no.entur.kakka.rest;

import no.entur.kakka.KakkaRouteBuilderIntegrationTestBase;
import no.entur.kakka.TestApp;
import org.apache.camel.EndpointInject;
import org.apache.camel.builder.AdviceWith;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The admin REST API over the Camel servlet, end to end through the embedded web server.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, classes = TestApp.class)
class AdminRestRouteIntegrationTest extends KakkaRouteBuilderIntegrationTestBase {

    @Autowired
    private TestRestTemplate restTemplate;

    @Value("${pubsub.kakka.outbound.topic.kingu.netex.export}")
    private String outboundTopicKinguNetexExport;

    @Value("${pubsub.kakka.outbound.topic.geocoder}")
    private String taskQueueTopic;

    @EndpointInject("mock:kinguNetexExport")
    protected MockEndpoint kinguNetexExport;

    @EndpointInject("mock:taskQueue")
    protected MockEndpoint taskQueue;

    @Test
    void exportEndpointPublishesExportJobs() throws Exception {
        AdviceWith.adviceWith(context, "netex-export-start-full",
                a -> a.weaveByToUri(outboundTopicKinguNetexExport).replace().to("mock:kinguNetexExport"));
        kinguNetexExport.expectedMessageCount(2);
        context.start();

        ResponseEntity<String> response = restTemplate.postForEntity("/services/export/stop_places/v2", null, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("done");
        kinguNetexExport.assertIsSatisfied();
    }

    @Test
    void buildPipelineEndpointQueuesRequestedTask() throws Exception {
        AdviceWith.adviceWith(context, "task-start-batch",
                a -> a.weaveByToUri(taskQueueTopic).replace().to("mock:taskQueue"));
        taskQueue.expectedMessageCount(1);
        context.start();

        ResponseEntity<String> response = restTemplate.postForEntity(
                "/services/geocoder_admin/build_pipeline?task=ADMINISTRATIVE_UNITS_DOWNLOAD", null, String.class);

        // The route clears the body, so Camel answers 204 (same as with Camel 4.4)
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        taskQueue.assertIsSatisfied();
        assertThat(taskQueue.getExchanges().getFirst().getIn().getBody(String.class))
                .contains("direct:kartverketAdministrativeUnitsDownload");
    }

    @Test
    void unknownPathReturnsNotFound() throws Exception {
        context.start();

        ResponseEntity<String> response = restTemplate.getForEntity("/services/no_such_endpoint", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}
