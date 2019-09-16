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

package no.entur.kakka.geocoder.routes.control;

import no.entur.kakka.Constants;
import no.entur.kakka.KakkaRouteBuilderIntegrationTestBase;
import no.entur.kakka.routes.status.JobEvent;
import no.entur.kakka.geocoder.GeoCoderConstants;
import org.apache.camel.EndpointInject;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.AdviceWithRouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.model.ModelCamelContext;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,classes = GeoCoderControlRouteBuilder.class, properties = "spring.main.sources=no.entur.kakka.test")
public class GeoCoderControlRouteIntegrationTest extends KakkaRouteBuilderIntegrationTestBase {

	@Autowired
	private ModelCamelContext context;

	@EndpointInject(uri = "mock:destination")
	protected MockEndpoint destination;

	@Produce(uri = "activemq:queue:GeoCoderQueue")
	protected ProducerTemplate geoCoderQueueTemplate;

	@EndpointInject(uri = "mock:statusQueue")
	protected MockEndpoint statusQueueMock;

	@Value("${geocoder.max.retries:3000}")
	private int maxRetries;

	@Before
	public void before() {
		destination.reset();
		statusQueueMock.reset();
	}

	@Test
	public void testMessagesAreMergedAndTaskedOrderAccordingToPhase() throws Exception {
		destination.expectedMessageCount(4);

		GeoCoderTask task1 = task(GeoCoderTask.Phase.DOWNLOAD_SOURCE_DATA);
		GeoCoderTask task2 = task(GeoCoderTask.Phase.TIAMAT_UPDATE);
		GeoCoderTask task3 = task(GeoCoderTask.Phase.TIAMAT_EXPORT);
		GeoCoderTask task4 = task(GeoCoderTask.Phase.PELIAS_UPDATE);

		destination.expectedBodiesReceived(task1, task2, task3, task4);
		context.start();

		geoCoderQueueTemplate.sendBody(new GeoCoderTaskMessage(task3).toString());
		geoCoderQueueTemplate.sendBody(new GeoCoderTaskMessage(task2, task4).toString());
		geoCoderQueueTemplate.sendBody(new GeoCoderTaskMessage(task1).toString());

		destination.assertIsSatisfied();
	}

	@Test
	public void testOngoingTasksAreAllowedToComplete() throws Exception {
		GeoCoderTask ongoingInit = task(GeoCoderTask.Phase.PELIAS_UPDATE);
		ongoingInit.setSubStep(1);
		GeoCoderTask ongoingFinalStep = task(GeoCoderTask.Phase.PELIAS_UPDATE);
		ongoingFinalStep.setSubStep(2);
		GeoCoderTask earlierPhase = task(GeoCoderTask.Phase.TIAMAT_UPDATE);

		// First task is rescheduled for step 2
		destination.whenExchangeReceived(1, e -> e.setProperty(GeoCoderConstants.GEOCODER_NEXT_TASK, ongoingFinalStep));
		destination.expectedBodiesReceived(ongoingInit, ongoingFinalStep, earlierPhase);

		context.start();

		geoCoderQueueTemplate.sendBody(new GeoCoderTaskMessage(ongoingInit, earlierPhase).toString());

		destination.assertIsSatisfied();
	}

	@Test
	public void testTaskIsDehydratedAndRehydratedWithHeaders() throws Exception {

		String headerValue = "fileNametest";
		GeoCoderTask task = task(GeoCoderTask.Phase.DOWNLOAD_SOURCE_DATA);
		GeoCoderTask taskNextIteration = task(GeoCoderTask.Phase.DOWNLOAD_SOURCE_DATA);

		destination.whenExchangeReceived(1, e -> {
			Assert.assertEquals(task, e.getProperty(GeoCoderConstants.GEOCODER_CURRENT_TASK, GeoCoderTask.class));
			e.getIn().setHeader(Constants.FILE_NAME, headerValue);
			e.setProperty(GeoCoderConstants.GEOCODER_NEXT_TASK, taskNextIteration);
		});

		destination.whenExchangeReceived(2, e -> {
			Assert.assertEquals(taskNextIteration, e.getProperty(GeoCoderConstants.GEOCODER_CURRENT_TASK, GeoCoderTask.class));
			Assert.assertEquals(headerValue, e.getIn().getHeader(Constants.FILE_NAME));
		});

		destination.expectedBodiesReceived(task, taskNextIteration);

		context.start();

		geoCoderQueueTemplate.sendBody(new GeoCoderTaskMessage(task).toString());

		destination.assertIsSatisfied();
	}

	@Test
	public void testTimeout() throws Exception {

		context.getRouteDefinition("geocoder-reschedule-task").adviceWith(context, new AdviceWithRouteBuilder() {
			@Override
			public void configure() throws Exception {
				interceptSendToEndpoint("direct:updateStatus")
						.skipSendToOriginalEndpoint().to("mock:statusQueue");
			}
		});
		statusQueueMock
				.whenExchangeReceived(1, e -> Assert.assertTrue(e.getIn().getBody(String.class).contains(JobEvent.State.TIMEOUT.toString())));
		statusQueueMock.expectedMessageCount(1);
		destination.whenExchangeReceived(1, e ->
			e.setProperty(GeoCoderConstants.GEOCODER_RESCHEDULE_TASK, true)
		);

		context.start();
		GeoCoderTask task = task(GeoCoderTask.Phase.DOWNLOAD_SOURCE_DATA);
		task.getHeaders().put(Constants.LOOP_COUNTER, maxRetries);
		task.getHeaders().put(Constants.SYSTEM_STATUS, JobEvent.builder().startGeocoder(GeoCoderTaskType.ADDRESS_DOWNLOAD).build().toString());

		geoCoderQueueTemplate.sendBody(new GeoCoderTaskMessage(task).toString());

		destination.assertIsSatisfied();
		statusQueueMock.assertIsSatisfied();
	}

	private GeoCoderTask task(GeoCoderTask.Phase phase) {
		return new GeoCoderTask(phase, "mock:destination");
	}

}
