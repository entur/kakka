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

package no.entur.kakka.geocoder.services;


import no.jskdata.Downloader;
import no.jskdata.GeoNorgeDownloadAPI;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import java.util.UUID;

public class KartverketServiceTest {

	@Test
	public void testAPIDownloaderIsUsedForUUIDs() {
		Downloader downloader = new KartverketService().getDownloader(UUID.randomUUID().toString(), null);
		Assertions.assertTrue(downloader instanceof GeoNorgeDownloadAPI);
	}

	@Test
	public void testWebPageDownloaderIsUsedForNonUUIDs() {
		Assertions.assertThrows(IllegalArgumentException.class, () -> {
			Downloader downloader = new KartverketService().getDownloader("not-a-uuid", null);
		});
	}
}
