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

package no.entur.kakka.geocoder.routes.kartverket;

import com.amazonaws.util.StringInputStream;
import no.entur.kakka.Constants;
import no.entur.kakka.KakkaRouteBuilderIntegrationTestBase;
import no.entur.kakka.TestApp;
import no.entur.kakka.geocoder.services.KartverketService;
import no.entur.kakka.repository.InMemoryBlobStoreRepository;
import org.apache.camel.Exchange;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.model.ModelCamelContext;
import org.apache.camel.spi.IdempotentRepository;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, classes = TestApp.class)
public class KartverketFileRouteBuilderIntegrationTest extends KakkaRouteBuilderIntegrationTestBase {
	@Autowired
	private ModelCamelContext context;

	@Autowired
	private InMemoryBlobStoreRepository inMemoryBlobStoreRepository;


	@Autowired
	private IdempotentRepository idempotentDownloadRepository;

	@MockBean
	public KartverketService kartverketService;

	@Produce(uri = "direct:uploadUpdatedFiles")
	protected ProducerTemplate uploadUpdatedFilesTemplate;

	private final String blobFolder = "blobTest";

	@Test
	public void testNewFilesAreUploadedToBlobStore() throws Exception {
		idempotentDownloadRepository.clear();
		String[] fileNames=new String[]{"1","2","3"};
		when(kartverketService.downloadFiles(anyString(), anyString(), anyString())).thenReturn(files(fileNames));

		context.start();
		startUpdate(blobFolder, true);

		assertBlobs(blobFolder,fileNames);

		// Verify that second invocation does nothing if content is unchanged
		startUpdate(blobFolder, false);


		String otherBlobFolder = "otherBlobTest";
		startUpdate(otherBlobFolder, true);

		assertBlobs(otherBlobFolder,fileNames);
	}

	private void assertBlobs(String path, String... objectNames) {
		Arrays.stream(objectNames).forEach(o -> Assertions.assertNotNull(inMemoryBlobStoreRepository.getBlob(path + "/" + o)));
	}

	@Test
	public void testNoLongerActiveFilesAreDeletedFromBlobStore() throws Exception {
		idempotentDownloadRepository.clear();
		inMemoryBlobStoreRepository.uploadBlob(blobFolder + "/1", new StringInputStream("1"), false);
		inMemoryBlobStoreRepository.uploadBlob(blobFolder + "/2", new StringInputStream("2"), false);
		inMemoryBlobStoreRepository.uploadBlob(blobFolder + "/3", new StringInputStream("3"), false);
		when(kartverketService.downloadFiles(anyString(), anyString(), anyString())).thenReturn(files("1"));

		context.start();
		startUpdate(blobFolder, true);

		assertBlobs(blobFolder,"1");
		Assertions.assertNull(inMemoryBlobStoreRepository.getBlob(blobFolder + "/2"));
		Assertions.assertNull(inMemoryBlobStoreRepository.getBlob(blobFolder + "/3"));
	}


	private void startUpdate(String blobFolder, boolean shouldChangeContent) {
		Exchange exchange = uploadUpdatedFilesTemplate.request("direct:uploadUpdatedFiles", e -> {
			e.getIn().setHeader(Constants.FOLDER_NAME, blobFolder);
			e.getIn().setHeader(Constants.KARTVERKET_DATASETID, "testID");
			e.getIn().setHeader(Constants.KARTVERKET_FORMAT, "testFormat");
		});

		Assertions.assertEquals(shouldChangeContent, Boolean.TRUE.equals(exchange.getIn().getHeader(Constants.CONTENT_CHANGED)));
	}

	private List<File> files(String... names) throws Exception {
		List<File> files = Arrays.stream(names).map(n -> new File("target/files/" + n)).collect(Collectors.toList());
		for (File file : files) {
			FileUtils.writeStringToFile(file, file.getName());
		}
		return files;
	}


}
