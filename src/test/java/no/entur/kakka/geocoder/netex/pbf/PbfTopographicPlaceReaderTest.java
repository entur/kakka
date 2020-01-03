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

package no.entur.kakka.geocoder.netex.pbf;


import no.entur.kakka.domain.OSMPOIFilter;
import org.junit.Assert;
import org.junit.Test;
import org.rutebanken.netex.model.IanaCountryTldEnumeration;
import org.rutebanken.netex.model.TopographicPlace;
import org.rutebanken.netex.model.TopographicPlaceTypeEnumeration;

import java.io.File;
import java.util.Arrays;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingDeque;

public class PbfTopographicPlaceReaderTest {

	@Test
	public void testParsePbfSampleFile() throws Exception {
		PbfTopographicPlaceReader reader =
				new PbfTopographicPlaceReader(Arrays.asList(createFilter("leisure", "common"), createFilter("naptan:indicator", "")), IanaCountryTldEnumeration.NO,
						                             new File("src/test/resources/no/entur/kakka/geocoder/pbf/sample.pbf"));

		BlockingQueue<TopographicPlace> queue = new LinkedBlockingDeque<>();
		reader.addToQueue(queue);

		Assert.assertEquals(4, queue.size());

		for (TopographicPlace tp : queue) {
			Assert.assertEquals(IanaCountryTldEnumeration.NO, tp.getCountryRef().getRef());
			Assert.assertEquals(TopographicPlaceTypeEnumeration.PLACE_OF_INTEREST, tp.getTopographicPlaceType());
			Assert.assertNotNull(tp.getName());
			Assert.assertNotNull(tp.getCentroid());
		}

	}

	private OSMPOIFilter createFilter(String key, String value) {
		return OSMPOIFilter.fromKeyAndValue(key, value);
	}
}
