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
import no.entur.kakka.task.BaseRouteBuilder;
import no.entur.kakka.task.TaskConstants;
import no.entur.kakka.routes.status.JobEvent;
import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.apache.camel.Message;
import org.apache.camel.processor.aggregate.GroupedMessageAggregationStrategy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.util.Collection;
import java.util.List;


@Component
public class TaskControlRouteBuilder extends BaseRouteBuilder {


    private static final String TASK_MESSAGE = "RutebankenTaskTaskMessage";

    @Value("${task.max.retries:600}")
    private int maxRetries;

    @Value("${task.retry.delay:15000}")
    private int retryDelay;

    @Value("${pubsub.kakka.outbound.topic.geocoder}")
    private String taskQueueTopic;

    @Value("${pubsub.kakka.inbound.subscription.geocoder}")
    private String taskQueueSubscription;

    private TaskMessage createMessageFromTaskTypes(Collection<TaskType> taskTypes) {
        return new TaskMessage(taskTypes.stream().map(TaskType::getTaskTask).toList());
    }

    @Override
    public void configure() throws Exception {
        super.configure();

        from("direct:taskStart")
                .process(e -> e.getIn().setBody(new TaskMessage(e.getIn().getBody(Task.class)).toString()))
                .to(taskQueueTopic)
                .routeId("task-start");

        from("direct:taskStartBatch")
                .process(e -> e.getIn().setBody(createMessageFromTaskTypes(e.getIn().getBody(Collection.class)).toString()))
                .to(taskQueueTopic)
                .routeId("task-start-batch");


        singletonFrom(taskQueueSubscription)
                .autoStartup("{{task.autoStartup:true}}")
                .process(this::removeSynchronizationForAggregatedExchange)
                .aggregate(constant(true)).aggregationStrategy(new GroupedMessageAggregationStrategy()).completionSize(100).completionTimeout(1000)
                .process(this::addSynchronizationForAggregatedExchange)
                .log(LoggingLevel.INFO, "Aggregated ${exchangeProperty.CamelAggregatedSize} Task requests (aggregation completion triggered by ${exchangeProperty.CamelAggregatedCompletedBy}).")
                .to("direct:taskMergeTaskMessages")
                .setProperty(TASK_MESSAGE, simple("${body}"))
                .to("direct:taskRehydrate")

                .to("direct:taskDelayIfRetry")
                .log(LoggingLevel.INFO, getClass().getName(), "Processing: ${body}. QueuedTasks: ${exchangeProperty." + TASK_MESSAGE + ".tasks.size}")
                .toD("${body.endpoint}")


                .choice()
                .when(simple("${exchangeProperty." + TaskConstants.RESCHEDULE_TASK + "}"))
                .to("direct:taskRescheduleTask")
                .otherwise()
                .removeHeader(Constants.LOOP_COUNTER)
                .end()

                .setBody(simple("${exchangeProperty." + TASK_MESSAGE + "}"))
                .to("direct:taskDehydrate")

                .choice()
                .when(simple("${body.complete}"))
                .log(LoggingLevel.INFO, getClass().getName(), "Task route completed")
                .otherwise()
                .convertBodyTo(String.class)
                .to(taskQueueTopic)
                .end()

                .routeId("task-main-route");

        from("direct:taskDelayIfRetry")
                .choice()
                .when(simple("${header." + Constants.LOOP_COUNTER + "} > 0"))
                .log(LoggingLevel.INFO, getClass().getName(), "Delay processing of: ${body}. Retry no: ${header." + Constants.LOOP_COUNTER + "}")
                .delay(retryDelay)
                .end()
                .routeId("task-delay-retry");

        from("direct:taskMergeTaskMessages")
                .process(e -> e.getIn().setBody(merge(e)))
                .routeId("task-merge-messages");

        from("direct:taskRehydrate")
                .process(this::rehydrate)
                .routeId("task-rehydrate-task");

        from("direct:taskDehydrate")
                .process(this::dehydrate)
                .routeId("task-dehydrate-task");

        from("direct:taskRescheduleTask")
                .process(e -> e.getIn().setHeader(Constants.LOOP_COUNTER, e.getIn().getHeader(Constants.LOOP_COUNTER, 0, Integer.class) + 1))
                .choice()
                .when(e -> e.getIn().getHeader(Constants.LOOP_COUNTER, Integer.class) > maxRetries)
                .log(LoggingLevel.WARN, getClass().getName(), "${header." + TaskConstants.CURRENT_TASK + "} timed out. Config should probably be tweaked. Not rescheduling.")
                .process(e -> JobEvent.systemJobBuilder(e).state(JobEvent.State.TIMEOUT).build()).to("direct:updateStatus")
                .otherwise()
                .setProperty(TaskConstants.NEXT_TASK, simple("${header." + TaskConstants.CURRENT_TASK + "}"))
                .end()
                .routeId("task-reschedule-task");
    }


    private void rehydrate(Exchange e) {
        TaskMessage msg = e.getIn().getBody(TaskMessage.class);
        Task task = msg.popNextTask();
        task.getHeaders().forEach((k, v) -> e.getIn().setHeader(k, v));
        e.setProperty(TaskConstants.CURRENT_TASK, task);
        e.getIn().setBody(task);
    }


    private void dehydrate(Exchange e) {
        Task nextTask = e.getProperty(TaskConstants.NEXT_TASK, Task.class);
        if (nextTask != null) {
            e.getIn().getBody(TaskMessage.class).addTask(nextTask);
            e.getIn().getHeaders().entrySet().stream().filter(entry -> entry.getKey().startsWith("Rutebanken")).forEach(entry -> nextTask.getHeaders().put(entry.getKey(), entry.getValue()));
        }
    }


    private TaskMessage merge(Exchange e) {
        Collection<Message> messages = e.getIn().getBody(List.class);
        TaskMessage merged = new TaskMessage();

        if (!CollectionUtils.isEmpty(messages)) {
            for (Message msg : messages) {
                try {
                    // TODO merge smarter, keep oldest. log discard?
                    String jsonString = new String((byte[]) msg.getBody());
                    TaskMessage taskMessage = TaskMessage.fromString(jsonString);
                    merged.getTasks().addAll(taskMessage.getTasks());
                } catch (Exception ex) {
                    log.warn("Discarded unparseable text msg: {}. Exception:{}", msg, ex.getMessage(), ex);
                }
            }
        }

        return merged;
    }

}
