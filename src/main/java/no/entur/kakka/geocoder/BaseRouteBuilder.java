package no.entur.kakka.geocoder;


import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.ServiceStatus;
import org.apache.camel.component.hazelcast.policy.HazelcastRoutePolicy;
import org.apache.camel.model.RouteDefinition;
import org.apache.camel.spi.RouteContext;
import org.apache.camel.spi.RoutePolicy;
import org.apache.camel.spi.Synchronization;
import org.apache.camel.spring.SpringRouteBuilder;
import org.entur.pubsub.camel.EnturGooglePubSubConstants;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gcp.pubsub.support.BasicAcknowledgeablePubsubMessage;

import java.util.List;
import java.util.stream.Collectors;

import static no.entur.kakka.Constants.SINGLETON_ROUTE_DEFINITION_GROUP_NAME;

/**
 * Defines common route behavior.
 */
public abstract class BaseRouteBuilder extends SpringRouteBuilder {

    @Value("${kakka.camel.redelivery.max:3}")
    private int maxRedelivery;

    @Value("${kakka.camel.redelivery.delay:5000}")
    private int redeliveryDelay;

    @Value("${kakka.camel.redelivery.backoff.multiplier:3}")
    private int backOffMultiplier;


    @Override
    public void configure() throws Exception {
        errorHandler(defaultErrorHandler()
                .redeliveryDelay(redeliveryDelay)
                .maximumRedeliveries(maxRedelivery)
                .onRedelivery(exchange -> logRedelivery(exchange))
                .useExponentialBackOff()
                .backOffMultiplier(backOffMultiplier)
                .logExhausted(true)
                .logRetryStackTrace(true));

    }

    protected void logRedelivery(Exchange exchange) {
        int redeliveryCounter = exchange.getIn().getHeader("CamelRedeliveryCounter", Integer.class);
        int redeliveryMaxCounter = exchange.getIn().getHeader("CamelRedeliveryMaxCounter", Integer.class);
        log.warn("Exchange failed. Redelivering the message locally, attempt {}/{}...", redeliveryCounter, redeliveryMaxCounter);
    }

    /**
     * Add ACK/NACK completion callback for an aggregated exchange.
     * The callback should be added after the aggregation is complete to prevent individual messages from being acked
     * by the aggregator.
     */
    protected void addOnCompletionForAggregatedExchange(Exchange exchange) {

        List<Message> messages = (List<Message>) exchange.getIn().getBody(List.class);
        List<BasicAcknowledgeablePubsubMessage> ackList = messages.stream()
                .map(m->m.getHeader(EnturGooglePubSubConstants.ACK_ID, BasicAcknowledgeablePubsubMessage.class))
                .collect(Collectors.toList());

        exchange.addOnCompletion(new Synchronization() {

            @Override
            public void onComplete(Exchange exchange) {
                ackList.stream().forEach(e->e.ack());
            }

            @Override
            public void onFailure(Exchange exchange) {
                ackList.stream().forEach(e->e.nack());
            }
        });
    }


    /**
     * Create a new singleton route definition from URI. Only one such route should be active throughout the cluster at any time.
     */
    protected RouteDefinition singletonFrom(String uri) {
        return this.from(uri).group(SINGLETON_ROUTE_DEFINITION_GROUP_NAME);
    }


    /**
     * Singleton route is only active if it is started and this node is the cluster leader for the route
     */
    protected boolean isSingletonRouteActive(String routeId) {
        return isStarted(routeId) && isLeader(routeId);
    }

    protected boolean isStarted(String routeId) {
        ServiceStatus status = getContext().getRouteStatus(routeId);
        return status != null && status.isStarted();
    }

    protected boolean isLeader(String routeId) {
        RouteContext routeContext = getContext().getRoute(routeId).getRouteContext();
        List<RoutePolicy> routePolicyList = routeContext.getRoutePolicyList();
        if (routePolicyList != null) {
            for (RoutePolicy routePolicy : routePolicyList) {
                if (routePolicy instanceof HazelcastRoutePolicy) {
                    return ((HazelcastRoutePolicy) (routePolicy)).isLeader();
                }
            }
        }
        return false;
    }


}
