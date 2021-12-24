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

package no.entur.kakka.geocoder.netex.geojson;

import no.entur.kakka.geocoder.geojson.GeojsonFeatureWrapperFactory;
import no.entur.kakka.geocoder.netex.TopographicPlaceAdapter;
import no.entur.kakka.geocoder.netex.TopographicPlaceMapper;
import no.entur.kakka.geocoder.netex.TopographicPlaceReader;
import org.apache.commons.io.FileUtils;
import org.geotools.feature.FeatureIterator;
import org.geotools.geojson.feature.FeatureJSON;
import org.rutebanken.netex.model.MultilingualString;
import org.rutebanken.netex.model.TopographicPlace;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.BlockingQueue;

/**
 * For reading collections of features from geojson files.
 */
public class GeoJsonCollectionTopographicPlaceReader implements TopographicPlaceReader {

    private final File[] files;

    private static final String LANGUAGE = "en";

    private static final String PARTICIPANT_REF = "KVE";

    private final GeojsonFeatureWrapperFactory wrapperFactory;


    public GeoJsonCollectionTopographicPlaceReader(GeojsonFeatureWrapperFactory wrapperFactory, File... files) {
        this.files = files;
        this.wrapperFactory = wrapperFactory;
    }

    public void addToQueue(BlockingQueue<TopographicPlace> queue) throws IOException, InterruptedException {
        for (File file : files) {
            FeatureJSON fJson = new FeatureJSON();
            FeatureIterator<org.opengis.feature.simple.SimpleFeature> itr = fJson.streamFeatureCollection(FileUtils.openInputStream(file));

            while (itr.hasNext()) {
                TopographicPlaceAdapter adapter = wrapperFactory.createWrapper(itr.next());
                TopographicPlace topographicPlace = new TopographicPlaceMapper(adapter, PARTICIPANT_REF).toTopographicPlace();

                if (topographicPlace != null) {
                    queue.put(topographicPlace);
                }
            }
            itr.close();
        }
    }

    @Override
    public String getParticipantRef() {
        return PARTICIPANT_REF;
    }

    @Override
    public MultilingualString getDescription() {
        return new MultilingualString().withLang(LANGUAGE).withValue("Kartverket administrative units");
    }
}
