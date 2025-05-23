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
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.rutebanken.netex.model.IanaCountryTldEnumeration;
import org.rutebanken.netex.model.KeyValueStructure;
import org.rutebanken.netex.model.TopographicPlace;
import org.rutebanken.netex.model.TopographicPlaceTypeEnumeration;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
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

        Assertions.assertEquals(4, queue.size());

        for (TopographicPlace tp : queue) {
            Assertions.assertEquals(IanaCountryTldEnumeration.NO, tp.getCountryRef().getRef());
            Assertions.assertEquals(TopographicPlaceTypeEnumeration.PLACE_OF_INTEREST, tp.getTopographicPlaceType());
            Assertions.assertNotNull(tp.getName());
            Assertions.assertNotNull(tp.getCentroid());
        }

    }

    @Test
    public void testParseMultiPolygonPbfFile() throws Exception {
        final List<OSMPOIFilter> osmPoiFilters = Arrays.asList(createFilter("tourism", "attraction"), createFilter("amenity", "theatre"));
        PbfTopographicPlaceReader reader =
                new PbfTopographicPlaceReader(osmPoiFilters, IanaCountryTldEnumeration.NO,
                        new File("src/test/resources/no/entur/kakka/geocoder/pbf/opera.pbf"));

        BlockingQueue<TopographicPlace> queue = new LinkedBlockingDeque<>();
        reader.addToQueue(queue);

        Assertions.assertEquals(1, queue.size());

        List<String> categories = new ArrayList<>();

        for (TopographicPlace tp : queue) {
            final List<KeyValueStructure> keyValue = tp.getKeyList().getKeyValue();
            for (KeyValueStructure keyValueStructure : keyValue) {
                var key = keyValueStructure.getKey();
                var value = keyValueStructure.getValue();
                var category = osmPoiFilters.stream()
                        .filter(f -> key.equals(f.getKey()) && value.equals(f.getValue()))
                        .map(OSMPOIFilter::getValue)
                        .findFirst();
                category.ifPresent(categories::add);
            }

            Assertions.assertEquals(2, categories.size());

            Assertions.assertEquals(IanaCountryTldEnumeration.NO, tp.getCountryRef().getRef());
            Assertions.assertEquals(TopographicPlaceTypeEnumeration.PLACE_OF_INTEREST, tp.getTopographicPlaceType());
            Assertions.assertNotNull(tp.getName());
            Assertions.assertNotNull(tp.getCentroid());
            Assertions.assertEquals("Operahuset i Oslo", tp.getName().getValue());
        }

    }


    /*
     * If distance between outer polygon is more den ca 20 m, POi should be ignored.
     */
    @Test
    public void testIgnoreMultiPolygons() throws Exception {
        PbfTopographicPlaceReader reader =
                new PbfTopographicPlaceReader(List.of(createFilter("tourism", "attraction")), IanaCountryTldEnumeration.NO,
                        new File("src/test/resources/no/entur/kakka/geocoder/pbf/uib.pbf"));

        BlockingQueue<TopographicPlace> queue = new LinkedBlockingDeque<>();
        reader.addToQueue(queue);

        Assertions.assertEquals(0, queue.size());

    }

    private OSMPOIFilter createFilter(String key, String value) {
        return OSMPOIFilter.fromKeyAndValue(key, value);
    }
}
