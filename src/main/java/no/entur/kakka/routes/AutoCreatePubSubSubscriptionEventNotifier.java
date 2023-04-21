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

import com.google.api.gax.rpc.AlreadyExistsException;
import com.google.cloud.spring.pubsub.PubSubAdmin;
import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.component.google.pubsub.GooglePubsubEndpoint;
import org.apache.camel.component.master.MasterEndpoint;
import org.apache.camel.spi.CamelEvent;
import org.apache.camel.support.DefaultInterceptSendToEndpoint;
import org.apache.camel.support.EventNotifierSupport;
import org.entur.pubsub.base.EnturGooglePubSubAdmin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Create PubSub topics and subscriptions on startup.
 * This is used only in unit tests and local environment.
 */
@Component
@Profile("google-pubsub-autocreate")
public class AutoCreatePubSubSubscriptionEventNotifier extends EventNotifierSupport {

    private static final Logger LOGGER = LoggerFactory.getLogger(AutoCreatePubSubSubscriptionEventNotifier.class);

    private final EnturGooglePubSubAdmin enturGooglePubSubAdmin;

    @Autowired
    private PubSubAdmin pubSubAdmin;

    private final String geoCoderQueueTopic;

    private final String geoCoderQueueSubscription;

    public AutoCreatePubSubSubscriptionEventNotifier(EnturGooglePubSubAdmin enturGooglePubSubAdmin,
                                                     @Value("${pubsub.kakka.outbound.topic.geocoder}") String geoCoderQueueTopic,
                                                     @Value("${pubsub.kakka.inbound.subscription.geocoder}") String geoCoderQueueSubscription) {
        this.enturGooglePubSubAdmin = enturGooglePubSubAdmin;
        this.geoCoderQueueTopic = getTopicSubscriptionName(geoCoderQueueTopic);
        this.geoCoderQueueSubscription = getTopicSubscriptionName(geoCoderQueueSubscription);
    }

    @Override
    public void notify(CamelEvent event) {

        if (event instanceof CamelEvent.CamelContextStartingEvent) {
            CamelContext context = ((CamelEvent.CamelContextStartingEvent) event).getContext();
            context.getEndpoints().stream().filter(e -> e.getEndpointUri().contains("google-pubsub:")).forEach(this::createSubscriptionIfMissing);
        }

    }

    private void createSubscriptionIfMissing(Endpoint e) {
        GooglePubsubEndpoint gep;
        if (e instanceof GooglePubsubEndpoint) {
            gep = (GooglePubsubEndpoint) e;
        } else if (e instanceof MasterEndpoint && ((MasterEndpoint) e).getEndpoint() instanceof GooglePubsubEndpoint) {
            gep = (GooglePubsubEndpoint) ((MasterEndpoint) e).getEndpoint();
        } else if (e instanceof DefaultInterceptSendToEndpoint && ((DefaultInterceptSendToEndpoint) e).getOriginalEndpoint() instanceof GooglePubsubEndpoint) {
            gep = (GooglePubsubEndpoint) ((DefaultInterceptSendToEndpoint) e).getOriginalEndpoint();
        } else if (e instanceof MasterEndpoint && ((MasterEndpoint) e).getEndpoint() instanceof DefaultInterceptSendToEndpoint) {
            gep = (GooglePubsubEndpoint) ((DefaultInterceptSendToEndpoint) ((MasterEndpoint) e).getEndpoint()).getOriginalEndpoint();
        } else {
            throw new IllegalStateException("Incompatible endpoint: " + e);
        }

        final String destination = gep.getDestinationName();
        if (destination.equals(geoCoderQueueTopic) || destination.equals(geoCoderQueueSubscription) ) {
            createSubscriptionTopic(geoCoderQueueTopic,geoCoderQueueSubscription);
        } else {
            enturGooglePubSubAdmin.createSubscriptionIfMissing(destination);
        }
    }

    private String getTopicSubscriptionName(String endpoint) {

        final String[] endpointParts = endpoint.split(":");

        if (endpointParts.length == 3) {
            return endpointParts[2];
        } else {
            throw new IllegalArgumentException(
                    "Google PubSub Endpoint format \"google-pubsub:projectId:destinationName[:subscriptionName]\"");
        }
    }

    /*
     * enturGooglePubSubAdmin.createSubscriptionIfMissing only support to create topic and subscription with same name
     * with this implementation we can set custom topics and subscriptions with different names
     */
    private void createSubscriptionTopic(String topicName,String subscriptionName) {

            try {
                pubSubAdmin.createTopic(topicName);
                LOGGER.debug("Created topic: {}", topicName);
            } catch (AlreadyExistsException e) {
                LOGGER.trace("Did not create topic: {}, as it already exists", topicName);
            }

            try {
                pubSubAdmin.createSubscription(subscriptionName, topicName);
                LOGGER.debug("Created subscription: {} with topic: {}", subscriptionName, topicName);
            } catch (AlreadyExistsException e) {
                LOGGER.trace("Did not create subscription: {}, as it already exists", subscriptionName);
            }
    }

}
