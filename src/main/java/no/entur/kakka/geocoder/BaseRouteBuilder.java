package no.entur.kakka.geocoder;


import com.google.cloud.spring.pubsub.support.BasicAcknowledgeablePubsubMessage;
import no.entur.kakka.exceptions.KakkaException;
import org.apache.camel.Consumer;
import org.apache.camel.Exchange;
import org.apache.camel.ExtendedExchange;
import org.apache.camel.Message;
import org.apache.camel.ServiceStatus;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.google.pubsub.GooglePubsubConstants;
import org.apache.camel.component.master.MasterConsumer;
import org.apache.camel.model.RouteDefinition;
import org.apache.camel.spi.Synchronization;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.commons.lang3.time.DateUtils;
import org.quartz.CronExpression;
import org.springframework.beans.factory.annotation.Value;

import java.text.ParseException;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import static org.apache.camel.component.google.pubsub.GooglePubsubConstants.ACK_ID;

/**
 * Defines common route behavior.
 */
public abstract class BaseRouteBuilder extends RouteBuilder {

    @Value("${kakka.camel.redelivery.max:3}")
    private int maxRedelivery;

    @Value("${kakka.camel.redelivery.delay:5000}")
    private int redeliveryDelay;

    @Value("${kakka.camel.redelivery.backoff.multiplier:3}")
    private int backOffMultiplier;

    @Value("${quartz.lenient.fire.time.ms:180000}")
    private int lenientFireTimeMs;


    @Override
    public void configure() throws Exception {
        errorHandler(defaultErrorHandler()
                .redeliveryDelay(redeliveryDelay)
                .maximumRedeliveries(maxRedelivery)
                .onRedelivery(this::logRedelivery)
                .useExponentialBackOff()
                .backOffMultiplier(backOffMultiplier)
                .logExhausted(true)
                .logRetryStackTrace(true));

        // Copy all PubSub headers except the internal Camel PubSub headers from the PubSub message into the Camel message headers.
        interceptFrom(".*google-pubsub:.*")
                .process(exchange ->
                {
                    Map<String, String> pubSubAttributes = exchange.getIn().getHeader(GooglePubsubConstants.ATTRIBUTES, Map.class);
                    if (pubSubAttributes == null) {
                        throw new IllegalStateException("Missing PubSub attribute maps in Exchange");
                    }
                    pubSubAttributes.entrySet()
                            .stream()
                            .filter(entry -> !entry.getKey().startsWith("CamelGooglePubsub"))
                            .forEach(entry -> exchange.getIn().setHeader(entry.getKey(), entry.getValue()));
                });

        // Copy all PubSub headers except the internal Camel PubSub headers from the Camel message into the PubSub message.
        interceptSendToEndpoint("google-pubsub:*").process(
                exchange -> {
                    Map<String, String> pubSubAttributes = new HashMap<>();
                    exchange.getIn().getHeaders().entrySet().stream()
                            .filter(entry -> !entry.getKey().startsWith("CamelGooglePubsub"))
                            .filter(entry -> Objects.toString(entry.getValue()).length() <= 1024)
                            .forEach(entry -> pubSubAttributes.put(entry.getKey(), Objects.toString(entry.getValue(), "")));
                    exchange.getIn().setHeader(GooglePubsubConstants.ATTRIBUTES, pubSubAttributes);

                });


    }

    protected void logRedelivery(Exchange exchange) {
        int redeliveryCounter = exchange.getIn().getHeader("CamelRedeliveryCounter", Integer.class);
        int redeliveryMaxCounter = exchange.getIn().getHeader("CamelRedeliveryMaxCounter", Integer.class);
        Throwable camelCaughtThrowable = exchange.getProperty("CamelExceptionCaught", Throwable.class);
        Throwable rootCause = ExceptionUtils.getRootCause(camelCaughtThrowable);

        String rootCauseType = rootCause != null ? rootCause.getClass().getName() : "";
        String rootCauseMessage = rootCause != null ? rootCause.getMessage() : "";

        log.warn("Exchange failed ({}: {}) . Redelivering the message locally, attempt {}/{}...", rootCauseType, rootCauseMessage, redeliveryCounter, redeliveryMaxCounter);
    }

    /**
     * Add ACK/NACK completion callback for an aggregated exchange.
     * The callback should be added after the aggregation is complete to prevent individual messages from being acked
     * by the aggregator.
     */
    protected void addOnCompletionForAggregatedExchange(Exchange exchange) {

        List<Message> messages = (List<Message>) exchange.getIn().getBody(List.class);
        List<BasicAcknowledgeablePubsubMessage> ackList = messages.stream()
                .map(m -> m.getHeader(ACK_ID, BasicAcknowledgeablePubsubMessage.class))
                .collect(Collectors.toList());

        exchange.adapt(ExtendedExchange.class).addOnCompletion(new AckSynchronization(ackList));

    }

    /**
     * Create a new singleton route definition from URI. Only one such route should be active throughout the cluster at any time.
     */
    protected RouteDefinition singletonFrom(String uri) {
        String lockName = getMasterLockName(uri);
        return this.from("master:" + lockName + ':' + uri);
    }

    /**
     * Create a lock name for an endpoint URI. The lock name should be unique across the Camel context so that each
     * route gets its own lock.
     * When using a file-based implementation for the camel-master lock (for local testing), the lock is created as a file in the local file system.
     * Thus the lock name should be a valid file name.
     * The lock name is built by stripping the component type (example: "google-pubsub:") and the endpoint parameters.
     * (example: "?synchronousPull=true")
     * @param uri the endpoint URI
     * @return a lock name
     */
    private String getMasterLockName(String uri) {
        if (uri.indexOf('?') != -1) {
            return uri.substring(uri.lastIndexOf(':') + 1, uri.indexOf('?'));
        }
        return uri.substring(uri.lastIndexOf(':') + 1);
    }

    /**
     * Quartz should only trigger if singleton route is started, this node is the cluster leader for the route and fireTime is (almost) same as scheduledFireTime.
     * <p>
     * To avoid multiple firings in cluster and re-firing as route is resumed upon change of leadership.
     */
    protected boolean shouldQuartzRouteTrigger(Exchange e, String cron) {
        CronExpression cronExpression;
        String cleanCron = cron.replace("+", " ");
        try {
            cronExpression = new CronExpression(cleanCron);
        } catch (ParseException pe) {
            throw new KakkaException("Invalid cron: " + cleanCron, pe);
        }
        return isStarted(e.getFromRouteId()) && isLeader(e.getFromRouteId()) && isScheduledQuartzFiring(e, cronExpression);
    }

    private boolean isScheduledQuartzFiring(Exchange exchange, CronExpression cron) {
        Date now = new Date();
        Date scheduledFireTime = cron.getNextValidTimeAfter(DateUtils.addMilliseconds(now, -lenientFireTimeMs));
        boolean isScheduledFiring = scheduledFireTime.equals(now) || scheduledFireTime.before(now);

        if (!isScheduledFiring) {
            log.warn("Ignoring quartz trigger for route {} at scheduled time {} as this is probably not a match for cron expression {} (checked at {})", exchange.getFromRouteId(), scheduledFireTime.getTime(), cron.getCronExpression(), now.getTime());
        }
        return isScheduledFiring;
    }


    protected boolean isStarted(String routeId) {
        ServiceStatus status = getContext().getRouteController().getRouteStatus(routeId);
        return status != null && status.isStarted();
    }

    protected boolean isLeader(String routeId) {
        Consumer consumer = getContext().getRoute(routeId).getConsumer();
        if (consumer instanceof MasterConsumer) {
            return ((MasterConsumer) consumer).isMaster();
        }
        return false;
    }

    private static class AckSynchronization implements Synchronization {

        private final List<BasicAcknowledgeablePubsubMessage> ackList;

        public AckSynchronization(List<BasicAcknowledgeablePubsubMessage> ackList) {
            this.ackList = ackList;
        }

        @Override
        public void onComplete(Exchange exchange) {
            ackList.forEach(BasicAcknowledgeablePubsubMessage::ack);
        }

        @Override
        public void onFailure(Exchange exchange) {
            ackList.forEach(BasicAcknowledgeablePubsubMessage::nack);
        }
    }


}
