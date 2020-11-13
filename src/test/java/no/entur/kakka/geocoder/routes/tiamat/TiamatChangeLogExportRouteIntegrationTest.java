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
import no.entur.kakka.geocoder.routes.tiamat.model.TiamatExportTask;
import no.entur.kakka.geocoder.routes.tiamat.model.TiamatExportTaskType;
import no.entur.kakka.geocoder.routes.tiamat.model.TiamatExportTasks;
import no.entur.kakka.repository.InMemoryBlobStoreRepository;
import no.entur.kakka.routes.status.JobEvent;
import org.apache.camel.EndpointInject;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, classes = TiamatChangeLogExportRouteBuilder.class, properties = "spring.main.sources=no.entur.kakka.test")
public class TiamatChangeLogExportRouteIntegrationTest extends KakkaRouteBuilderIntegrationTestBase {

    @EndpointInject("mock:changeLogExport")
    protected MockEndpoint changeLogExportMock;


    @EndpointInject("mock:updateStatus")
    protected MockEndpoint statusQueueMock;

    @Produce("direct:processTiamatChangeLogExportTask")
    protected ProducerTemplate input;

    @Value("${tiamat.publish.export.blobstore.subdirectory:tiamat}")
    private String blobStoreSubdirectoryForTiamatExport;


    @Autowired
    private InMemoryBlobStoreRepository inMemoryBlobStoreRepository;


    @Before
    public void setUp() {
        changeLogExportMock.reset();
        statusQueueMock.reset();
        try {

            replaceEndpoint("tiamat-publish-export-process-changelog", "direct:exportChangedStopPlaces", "mock:changeLogExport");

            replaceEndpoint("tiamat-publish-export-process-changelog", "direct:updateStatus", "mock:updateStatus");


        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }


    @Test
    public void uploadBlobAndUpdateEtcdWhenContentIsChanged() throws Exception {
        statusQueueMock.expectedMessageCount(1);

        changeLogExportMock.whenAnyExchangeReceived(e -> {
            e.getOut().setBody("Content from Tiamat");
            e.getOut().setHeaders(e.getIn().getHeaders());
        });

        TiamatExportTask changeLogTask = new TiamatExportTask("testExport", "queryParam=XXX", TiamatExportTaskType.CHANGE_LOG);

        context.start();

        input.request("direct:processTiamatChangeLogExportTask", ex -> {
            ex.setProperty(Constants.TIAMAT_EXPORT_TASKS, new TiamatExportTasks(changeLogTask));
            ex.getIn().setHeader(Constants.SYSTEM_STATUS, JobEvent.builder().correlationId("1").jobDomain(JobEvent.JobDomain.TIAMAT).state(JobEvent.State.STARTED).action("EXPORT").build().toString());
        });

        statusQueueMock.assertIsSatisfied();
        changeLogExportMock.assertIsSatisfied();
        Assert.assertEquals(1, inMemoryBlobStoreRepository.listBlobsFlat(blobStoreSubdirectoryForTiamatExport + "/" + changeLogTask.getName()).getFiles().size());

    }

    @Test
    public void doNotUpdateEtcdCntWhenNoChanges() throws Exception {
        TiamatExportTask changeLogTask = new TiamatExportTask("testExport", "?queryParam=XXX", TiamatExportTaskType.CHANGE_LOG);
        statusQueueMock.expectedMessageCount(1);


        changeLogExportMock.whenAnyExchangeReceived(e -> {
            Assert.assertTrue(e.getIn().getHeader(Constants.QUERY_STRING, String.class).startsWith(changeLogTask.getQueryString()));
            e.getOut().setBody(null);
            e.getOut().setHeaders(e.getIn().getHeaders());
        });

        context.start();


        input.request("direct:processTiamatChangeLogExportTask", ex -> {
            ex.setProperty(Constants.TIAMAT_EXPORT_TASKS, new TiamatExportTasks(changeLogTask));
            ex.getIn().setHeader(Constants.SYSTEM_STATUS, JobEvent.builder().correlationId("1").jobDomain(JobEvent.JobDomain.TIAMAT).state(JobEvent.State.STARTED).action("EXPORT").build().toString());
        });

        statusQueueMock.assertIsSatisfied();
        changeLogExportMock.assertIsSatisfied();
        Assert.assertEquals(0, inMemoryBlobStoreRepository.listBlobsFlat(blobStoreSubdirectoryForTiamatExport + "/" + changeLogTask.getName()).getFiles().size());
    }

}