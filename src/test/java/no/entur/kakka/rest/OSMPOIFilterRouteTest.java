package no.entur.kakka.rest;

import no.entur.kakka.KakkaRouteBuilderIntegrationTestBase;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.junit.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles(profiles = {"google-pubsub-emulator"}, inheritProfiles = true)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, classes = AdminRestRouteBuilder.class, properties = "spring.main.sources=no.entur.kakka.test")
public class OSMPOIFilterRouteTest extends KakkaRouteBuilderIntegrationTestBase {

    @Produce("rest:get:services/osmpoifilter")
    protected ProducerTemplate getTemplate;

    @Test
    public void testGetFilters() throws Exception {
        context.start();
        getTemplate.sendBody(null);
    }
}
