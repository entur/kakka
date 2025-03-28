package no.entur.kakka.geocoder.routes.tiamat;

import no.entur.kakka.Constants;
import no.entur.kakka.KakkaRouteBuilderIntegrationTestBase;
import no.entur.kakka.TestApp;
import org.apache.camel.EndpointInject;
import org.apache.camel.Message;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.AdviceWith;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, classes = TestApp.class)
class KinguPublishExportsRouteBuilderTest extends KakkaRouteBuilderIntegrationTestBase {

    @Produce("direct:startNetexExport")
    protected ProducerTemplate startNetexExport;

    @EndpointInject("mock:NetexExportQueue")
    protected MockEndpoint netexExportQueue;

    @Value("${pubsub.kakka.outbound.topic.kingu.netex.export}")
    private String outboundTopicKinguNetexExport;

    @Test
    void testNetexExportQueue() throws Exception {

        AdviceWith.adviceWith(context,"netex-export-start-full", a -> a.weaveByToUri(outboundTopicKinguNetexExport).replace()
                .to("mock:NetexExportQueue"));

        context.start();
        startNetexExport.sendBody("");

        final Message in = netexExportQueue.getExchanges().getFirst().getIn();
        final String body = in.getBody(String.class);
        Assertions.assertEquals(2,netexExportQueue.getExchanges().size());
        Assertions.assertFalse(body.isEmpty());
        Assertions.assertTrue(body.contains("Oslo"));
        Assertions.assertEquals(Constants.NETEX_EXPORT_STATUS_VALUE,in.getHeader(Constants.NETEX_EXPORT_STATUS_HEADER));

    }

}