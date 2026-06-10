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

package no.entur.kakka.task.routes.control;

import no.entur.kakka.Constants;
import no.entur.kakka.KakkaRouteBuilderIntegrationTestBase;
import no.entur.kakka.TestApp;
import no.entur.kakka.task.TaskConstants;
import no.entur.kakka.routes.status.JobEvent;
import org.apache.camel.EndpointInject;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.AdviceWith;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.model.ModelCamelContext;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, classes = TestApp.class)
@Disabled
public class TaskControlRouteIntegrationTest extends KakkaRouteBuilderIntegrationTestBase {

    @EndpointInject("mock:destination")
    protected MockEndpoint destination;
    @Produce("{{pubsub.kakka.outbound.topic.geocoder}}")
    protected ProducerTemplate taskQueueTemplate;
    @EndpointInject("mock:statusQueue")
    protected MockEndpoint statusQueueMock;
    @Autowired
    private ModelCamelContext context;
    @Value("${task.max.retries:3000}")
    private int maxRetries;

    @BeforeEach
    public void before() {
        destination.reset();
        statusQueueMock.reset();
    }

    @Test
    public void testMessagesAreMergedAndTaskedOrderAccordingToPhase() throws Exception {
        destination.expectedMessageCount(4);

        Task task1 = task(Task.Phase.DOWNLOAD_SOURCE_DATA);
        Task task2 = task(Task.Phase.TIAMAT_UPDATE);
        Task task3 = task(Task.Phase.TIAMAT_EXPORT);
        Task task4 = task(Task.Phase.COMPLETE);

        destination.expectedBodiesReceived(task1, task2, task3, task4);
        destination.setResultWaitTime(120_000);
        context.start();

        taskQueueTemplate.sendBody(new TaskMessage(task3).toString());
        taskQueueTemplate.sendBody(new TaskMessage(task2, task4).toString());
        taskQueueTemplate.sendBody(new TaskMessage(task1).toString());

        destination.assertIsSatisfied();
    }

    @Test
    public void testOngoingTasksAreAllowedToComplete() throws Exception {
        Task ongoingInit = task(Task.Phase.TIAMAT_EXPORT);
        ongoingInit.setSubStep(1);
        Task ongoingFinalStep = task(Task.Phase.TIAMAT_EXPORT);
        ongoingFinalStep.setSubStep(2);
        Task earlierPhase = task(Task.Phase.TIAMAT_UPDATE);

        // First task is rescheduled for step 2
        destination.whenExchangeReceived(1, e -> e.setProperty(TaskConstants.NEXT_TASK, ongoingFinalStep));
        destination.expectedBodiesReceived(ongoingInit, ongoingFinalStep, earlierPhase);
        destination.setResultWaitTime(120_000);

        context.start();

        taskQueueTemplate.sendBody(new TaskMessage(ongoingInit, earlierPhase).toString());

        destination.assertIsSatisfied();
    }

    @Test
    public void testTaskIsDehydratedAndRehydratedWithHeaders() throws Exception {

        String headerValue = "fileNametest";
        Task task = task(Task.Phase.DOWNLOAD_SOURCE_DATA);
        Task taskNextIteration = task(Task.Phase.DOWNLOAD_SOURCE_DATA);

        destination.whenExchangeReceived(1, e -> {
            Assertions.assertEquals(task, e.getProperty(TaskConstants.CURRENT_TASK, Task.class));
            e.getIn().setHeader(Constants.FILE_NAME, headerValue);
            e.setProperty(TaskConstants.NEXT_TASK, taskNextIteration);
        });

        destination.whenExchangeReceived(2, e -> {
            Assertions.assertEquals(taskNextIteration, e.getProperty(TaskConstants.CURRENT_TASK, Task.class));
            Assertions.assertEquals(headerValue, e.getIn().getHeader(Constants.FILE_NAME, String.class));
        });

        destination.expectedBodiesReceived(task, taskNextIteration);
        destination.setResultWaitTime(120_000);

        context.start();

        taskQueueTemplate.sendBody(new TaskMessage(task).toString());

        destination.assertIsSatisfied();
    }

    @Test
    public void testTimeout() throws Exception {

        AdviceWith.adviceWith(context, "task-reschedule-task",
                a -> a.interceptSendToEndpoint("direct:updateStatus")
                        .skipSendToOriginalEndpoint().to("mock:statusQueue"));

        statusQueueMock
                .whenExchangeReceived(1, e -> Assertions.assertTrue(e.getIn().getBody(String.class).contains(JobEvent.State.TIMEOUT.toString())));
        statusQueueMock.expectedMessageCount(1);
        destination.whenExchangeReceived(1, e ->
                e.setProperty(TaskConstants.RESCHEDULE_TASK, true)
        );
        destination.setResultWaitTime(120_000);

        context.start();
        Task task = task(Task.Phase.DOWNLOAD_SOURCE_DATA);
        task.getHeaders().put(Constants.LOOP_COUNTER, maxRetries);
        task.getHeaders().put(Constants.SYSTEM_STATUS, JobEvent.builder().startTask(TaskType.ADMINISTRATIVE_UNITS_DOWNLOAD).build().toString());

        taskQueueTemplate.sendBody(new TaskMessage(task).toString());

        destination.assertIsSatisfied();
        statusQueueMock.assertIsSatisfied();
    }

    private Task task(Task.Phase phase) {
        return new Task(phase, "mock:destination");
    }

}
