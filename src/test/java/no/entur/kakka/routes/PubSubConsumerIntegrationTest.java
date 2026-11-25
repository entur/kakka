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

package no.entur.kakka.routes;

import no.entur.kakka.Constants;
import no.entur.kakka.KakkaRouteBuilderIntegrationTestBase;
import no.entur.kakka.TestApp;
import no.entur.kakka.task.routes.control.Task;
import org.apache.camel.EndpointInject;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.AdviceWith;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Messages consumed from Pub/Sub through the emulator, on both a plain and a singleton (camel-master) consumer.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, classes = TestApp.class)
class PubSubConsumerIntegrationTest extends KakkaRouteBuilderIntegrationTestBase {

    private static final long RESULT_WAIT_MILLIS = 60_000;

    // Auto-created with the same name for topic and subscription
    private static final String KINGU_EXPORT_TOPIC = "ror.kakka.inbound.subscription.kingu.netex.export";

    @Produce("direct:taskStart")
    protected ProducerTemplate taskStart;

    @EndpointInject("mock:taskDestination")
    protected MockEndpoint taskDestination;

    @EndpointInject("mock:kinguExportUpload")
    protected MockEndpoint kinguExportUpload;

    @Test
    void taskIsPublishedAndConsumedBySingletonTaskRoute() throws Exception {
        Task task = new Task(Task.Phase.DOWNLOAD_SOURCE_DATA, "mock:taskDestination");
        taskDestination.expectedBodiesReceived(task);
        taskDestination.setResultWaitTime(RESULT_WAIT_MILLIS);
        context.start();

        taskStart.sendBody(task);

        taskDestination.assertIsSatisfied();
    }

    @Test
    void kinguExportMessageAttributesBecomeHeaders() throws Exception {
        AdviceWith.adviceWith(context, "from-tiamat-export-queue-processed",
                a -> a.weaveByToUri("direct:kinguExportUploadFileExternal").replace().to("mock:kinguExportUpload"));
        kinguExportUpload.expectedMessageCount(1);
        kinguExportUpload.expectedHeaderReceived(Constants.FILE_HANDLE, "export/oslo/export.zip");
        kinguExportUpload.expectedHeaderReceived(Constants.TARGET_FILE_HANDLE, "tiamat/Oslo_latest.zip");
        kinguExportUpload.setResultWaitTime(RESULT_WAIT_MILLIS);
        context.start();

        pubSubTemplate.publish(KINGU_EXPORT_TOPIC, "export done",
                Map.of("exportLocation", "oslo/export.zip", "exportJobName", "Oslo")).get(30, TimeUnit.SECONDS);

        kinguExportUpload.assertIsSatisfied();
    }
}
