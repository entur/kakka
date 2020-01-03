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
import no.entur.kakka.openstreetmap.model.OSMNode;
import org.junit.Assert;
import org.junit.Test;
import org.rutebanken.netex.model.IanaCountryTldEnumeration;
import org.rutebanken.netex.model.TopographicPlace;

import java.util.Arrays;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingDeque;

public class TopographicPlaceOsmContentHandlerTest {

	@Test
	public void testMissingNameDoesNotMatches() {
		Assert.assertFalse(handler().matchesFilter(node("amenity=test", "leisure=test", "key=start", "other=other")));
	}

	@Test
	public void testNameOnlyDoesNotMatches() {
		Assert.assertFalse(handler().matchesFilter(node("name=1", "other2=other", "other1=other")));
	}

	@Test
	public void testFullTagFilterMatches() {
		Assert.assertTrue(handler().matchesFilter(node("name=1", "amenity=test", "other=other")));
	}

	@Test
	public void testOnlyKeyFilterMatches() {
		Assert.assertTrue(handler().matchesFilter(node("name=1", "key=start")));
	}

	@Test
	public void testStartFilterMatches() {
		Assert.assertTrue(handler().matchesFilter(node("name=1", "key=startOTHER", "other=other")));
	}

	private TopographicPlaceOsmContentHandler handler() {

		OSMPOIFilter filterKey = createFilter("leisure", null);
		OSMPOIFilter filterFull = createFilter("amenity", "test");
		OSMPOIFilter filterStartsWith = createFilter("key", "start");

		return handler(filterKey, filterFull, filterStartsWith);
	}

	private TopographicPlaceOsmContentHandler handler(OSMPOIFilter... filter) {

		BlockingQueue<TopographicPlace> queue = new LinkedBlockingDeque<>();

		TopographicPlaceOsmContentHandler handler = new TopographicPlaceOsmContentHandler(queue, Arrays.asList(filter), "OSM", IanaCountryTldEnumeration.NO);
		return handler;
	}


	private OSMNode node(String... tags) {
		OSMNode node = new OSMNode();

		if (tags != null) {
			Arrays.stream(tags).forEach(t -> node.addTag(
					t.split("=")[0], t.split("=")[1]));
		}
		return node;
	}

	private OSMPOIFilter createFilter(String key, String value) {
		OSMPOIFilter filter = new OSMPOIFilter();
		filter.setKey(key);
		filter.setValue(value);
		return filter;
	}
}
