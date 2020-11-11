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

package no.entur.kakka.geocoder.routes.tiamat;

import no.entur.kakka.Constants;
import no.entur.kakka.KakkaRouteBuilderIntegrationTestBase;
import no.entur.kakka.geocoder.GeoCoderConstants;
import no.entur.kakka.geocoder.routes.tiamat.model.TiamatExportTask;
import no.entur.kakka.geocoder.routes.tiamat.model.TiamatExportTaskType;
import no.entur.kakka.geocoder.routes.tiamat.model.TiamatExportTasks;
import no.entur.kakka.routes.status.JobEvent;
import org.apache.camel.EndpointInject;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.AdviceWithRouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.model.ModelCamelContext;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.HashMap;
import java.util.Map;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, classes = TiamatPublishExportsRouteBuilder.class,
        properties = {
        "spring.main.sources=no.entur.kakka.test",
        "tiamat.export.retry.delay=1"
        })
public class TiamatPublishExportsRouteIntegrationTest extends KakkaRouteBuilderIntegrationTestBase {

    @Value("${tiamat.export.max.retries:480}")
    private int maxRetries;

    @Autowired
    private ModelCamelContext context;

    @EndpointInject(uri = "mock:updateStatus")
    protected MockEndpoint statusQueueMock;


    @EndpointInject(uri = "mock:tiamatExport")
    protected MockEndpoint tiamatStartExportMock;

    @EndpointInject(uri = "mock:tiamatPollJobStatus")
    protected MockEndpoint tiamatPollMock;

    @EndpointInject(uri = "mock:TiamatExportQueue")
    protected MockEndpoint rescheduleMock;

    @EndpointInject(uri = "mock:changeLogExportMock")
    protected MockEndpoint changeLogExportMock;


    @Produce(uri = "entur-google-pubsub:TiamatExportQueue")
    protected ProducerTemplate input;

    @Before
    public void setUp() {
        tiamatStartExportMock.reset();
        rescheduleMock.reset();
        statusQueueMock.reset();
        tiamatPollMock.reset();
        try {


            AdviceWithRouteBuilder.adviceWith(context, "tiamat-publish-export-poll-status", a ->{
                a.weaveByToUri("direct:tiamatPollJobStatus").replace().to("mock:tiamatPollJobStatus");
            });

            AdviceWithRouteBuilder.adviceWith(context, "tiamat-publish-export-start-new", a ->{
                a.weaveByToUri("direct:tiamatExport").replace().to("mock:tiamatExport");
            });

            AdviceWithRouteBuilder.adviceWith(context,"tiamat-publish-export", a -> {
                a.weaveByToUri("entur-google-pubsub:TiamatExportQueue").replace().to("mock:TiamatExportQueue");
            });

            AdviceWithRouteBuilder.adviceWith(context, "tiamat-publish-export-start-new", a ->{
                a.weaveByToUri("direct:updateStatus").replace().to("mock:updateStatus");
            });

            AdviceWithRouteBuilder.adviceWith(context, "tiamat-publish-export-poll-status", a ->{
                a.weaveByToUri("direct:updateStatus").replace().to("mock:updateStatus");
            });

            AdviceWithRouteBuilder.adviceWith(context, "tiamat-publish-export-start-new", a ->{
                a.weaveByToUri("direct:processTiamatChangeLogExportTask").replace().to("mock:changeLogExportMock");
            });


        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    public void newExportIsStarted() throws Exception {
        tiamatStartExportMock.expectedMessageCount(1);
        statusQueueMock.expectedMessageCount(1);
        rescheduleMock.expectedMessageCount(1);

        context.start();

        input.sendBody(new TiamatExportTasks(new TiamatExportTask("TaskName", "?query=something")).toString());

        tiamatStartExportMock.assertIsSatisfied();
        statusQueueMock.assertIsSatisfied();
        rescheduleMock.assertIsSatisfied();
    }

    @Test
    public void newChangeLogExportCompletedSynchronously() throws Exception {
        tiamatStartExportMock.expectedMessageCount(0);
        rescheduleMock.expectedMessageCount(0);
        changeLogExportMock.expectedMessageCount(1);

        context.start();

        input.sendBody(new TiamatExportTasks(new TiamatExportTask("TaskName", "?query=something", TiamatExportTaskType.CHANGE_LOG)).toString());

        tiamatStartExportMock.assertIsSatisfied();
        changeLogExportMock.assertIsSatisfied();
        rescheduleMock.assertIsSatisfied();
    }

    @Test
    public void incompleteTaskIsRescheduled() throws Exception {
        tiamatPollMock.expectedMessageCount(1);
        statusQueueMock.expectedMessageCount(0);
        rescheduleMock.expectedMessageCount(1);

        tiamatPollMock.whenAnyExchangeReceived(e -> e.setProperty(GeoCoderConstants.GEOCODER_RESCHEDULE_TASK, true));

        context.start();

        input.sendBodyAndHeader(new TiamatExportTasks(new TiamatExportTask("TaskName", "?query=something")).toString(), Constants.LOOP_COUNTER, 1);

        tiamatPollMock.assertIsSatisfied();
        statusQueueMock.assertIsSatisfied();
        rescheduleMock.assertIsSatisfied();
    }

    @Test
    public void incompleteTaskBeyondRetryLimitIsFailed() throws Exception {
        tiamatPollMock.expectedMessageCount(1);
        statusQueueMock.expectedMessageCount(1);
        rescheduleMock.expectedMessageCount(0);

        tiamatPollMock.whenAnyExchangeReceived(e -> e.setProperty(GeoCoderConstants.GEOCODER_RESCHEDULE_TASK, true));

        Map<String, Object> headers = new HashMap<>();
        headers.put(Constants.SYSTEM_STATUS, status().toString());
        headers.put(Constants.LOOP_COUNTER, maxRetries);

        context.start();

        input.sendBodyAndHeaders(new TiamatExportTasks(new TiamatExportTask("TaskName", "?query=something")).toString(), headers);

        tiamatPollMock.assertIsSatisfied();
        statusQueueMock.assertIsSatisfied();
        rescheduleMock.assertIsSatisfied();
    }

    @Test
    public void completeTaskGivesRescheduledMessageIfMoreTasksAreWaiting() throws Exception {
        tiamatPollMock.expectedMessageCount(1);
        statusQueueMock.expectedMessageCount(0);
        rescheduleMock.expectedMessageCount(1);

        tiamatPollMock.whenAnyExchangeReceived(e -> e.setProperty(GeoCoderConstants.GEOCODER_RESCHEDULE_TASK, false));

        context.start();

        input.sendBodyAndHeader(new TiamatExportTasks(new TiamatExportTask("TaskName", "?query=something"), new TiamatExportTask("AnotherTask", "?anotherQuery=xx")).toString(), Constants.LOOP_COUNTER, 1);

        tiamatPollMock.assertIsSatisfied();
        statusQueueMock.assertIsSatisfied();
        rescheduleMock.assertIsSatisfied();
    }

    @Test
    public void completeTaskGivesNoRescheduledMessageIfNoTasksAreWaiting() throws Exception {
        tiamatPollMock.expectedMessageCount(1);
        statusQueueMock.expectedMessageCount(0);
        rescheduleMock.expectedMessageCount(0);

        tiamatPollMock.whenAnyExchangeReceived(e -> e.setProperty(GeoCoderConstants.GEOCODER_RESCHEDULE_TASK, false));

        context.start();

        input.sendBodyAndHeader(new TiamatExportTasks(new TiamatExportTask("TaskName", "?query=something")).toString(), Constants.LOOP_COUNTER, 1);

        tiamatPollMock.assertIsSatisfied();
        statusQueueMock.assertIsSatisfied();
        rescheduleMock.assertIsSatisfied();
    }


    private JobEvent status() {
        return JobEvent.builder().jobDomain(JobEvent.JobDomain.TIAMAT).action("EXPORT").correlationId("corrId").state(JobEvent.State.STARTED).build();
    }
}
