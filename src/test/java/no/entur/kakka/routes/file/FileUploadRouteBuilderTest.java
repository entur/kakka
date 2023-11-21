package no.entur.kakka.routes.file;

import no.entur.kakka.KakkaRouteBuilderIntegrationTestBase;
import no.entur.kakka.TestApp;
import org.apache.camel.EndpointInject;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.AdviceWith;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockPart;

import java.util.List;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, classes = TestApp.class)
class FileUploadRouteBuilderTest extends KakkaRouteBuilderIntegrationTestBase {

    @Produce("direct:uploadFilesAndStartImport")
    protected ProducerTemplate uploadFilesAndStartImport;

    @EndpointInject("mock:tariffZoneFileQueue")
    protected MockEndpoint tariffZoneFileQueue;

    @EndpointInject("mock:direct:uploadBlob")
    protected MockEndpoint uploadBlob;

    @Test
    void testTariffZoneXmlFileIsUploadedAndValidationTaskAddedToQueue() throws Exception {

        AdviceWith.adviceWith(
                context,
                "upload-file-and-start-import",
                a -> a.weaveByToUri("google-pubsub:(.*):ror.kakka.outbound.topic.tariff.zone.file.queue")
                        .replace()
                        .to("mock:tariffZoneFileQueue")
        );

        replaceEndpoint("upload-file-and-start-import", "direct:uploadBlob", "mock:direct:uploadBlob");

        context.start();
        uploadFilesAndStartImport.sendBody(new MockPart("testFileName.xml", "testFileName.xml", null));

        uploadBlob.expectedMessageCount(1);
        tariffZoneFileQueue.expectedMessageCount(1);

        uploadBlob.assertIsSatisfied();
        tariffZoneFileQueue.assertIsSatisfied();
    }

    @Test
    void testMultipleTariffZoneXmlFileAreUploadedAndValidationTasksAddedToQueue() throws Exception {

        AdviceWith.adviceWith(
                context,
                "upload-file-and-start-import",
                a -> a.weaveByToUri("google-pubsub:(.*):ror.kakka.outbound.topic.tariff.zone.file.queue")
                        .replace()
                        .to("mock:tariffZoneFileQueue")
        );

        replaceEndpoint("upload-file-and-start-import", "direct:uploadBlob", "mock:direct:uploadBlob");

        context.start();
        uploadFilesAndStartImport.sendBody(
                List.of(
                        new MockPart("testFileName.xml", "testFileName.xml", null),
                        new MockPart("testFileName2.xml", "testFileName2.xml", null)
                )
        );

        uploadBlob.expectedMessageCount(2);
        tariffZoneFileQueue.expectedMessageCount(2);

        uploadBlob.assertIsSatisfied();
        tariffZoneFileQueue.assertIsSatisfied();
    }

    @Test
    void testNonXmlFileIsIgnored() throws Exception {

        AdviceWith.adviceWith(
                context,
                "upload-file-and-start-import",
                a -> a.weaveByToUri("google-pubsub:(.*):ror.kakka.outbound.topic.tariff.zone.file.queue")
                        .replace()
                        .to("mock:tariffZoneFileQueue")
        );

        replaceEndpoint("upload-file-and-start-import", "direct:uploadBlob", "mock:direct:uploadBlob");

        context.start();
        uploadFilesAndStartImport.sendBody(new MockPart("testFileName.zip", "testFileName.zip", null));

        uploadBlob.expectedMessageCount(0);
        tariffZoneFileQueue.expectedMessageCount(0);

        uploadBlob.assertIsSatisfied();
        tariffZoneFileQueue.assertIsSatisfied();
    }
}