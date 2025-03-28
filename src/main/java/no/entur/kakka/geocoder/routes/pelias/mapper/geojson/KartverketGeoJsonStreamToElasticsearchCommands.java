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

package no.entur.kakka.geocoder.routes.pelias.mapper.geojson;

import no.entur.kakka.geocoder.featurejson.FeatureJSONCollection;
import no.entur.kakka.geocoder.geojson.GeojsonFeatureWrapperFactory;
import no.entur.kakka.geocoder.netex.TopographicPlaceAdapter;
import no.entur.kakka.geocoder.routes.pelias.elasticsearch.ElasticsearchCommand;
import no.entur.kakka.geocoder.routes.pelias.mapper.kartverket.BoroughToPeliasDocument;
import no.entur.kakka.geocoder.routes.pelias.mapper.kartverket.CountyToPeliasDocument;
import no.entur.kakka.geocoder.routes.pelias.mapper.kartverket.LocalityToPeliasDocument;
import no.entur.kakka.geocoder.routes.pelias.mapper.kartverket.PlaceToPeliasDocument;
import no.entur.kakka.geocoder.routes.pelias.mapper.kartverket.TopographicPlaceAdapterToPeliasDocument;
import org.geotools.api.feature.simple.SimpleFeature;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.Collection;
import java.util.Objects;

@Service
public class KartverketGeoJsonStreamToElasticsearchCommands {

    private final GeojsonFeatureWrapperFactory wrapperFactory;

    private final long placeBoost;

    public KartverketGeoJsonStreamToElasticsearchCommands(@Autowired GeojsonFeatureWrapperFactory wrapperFactory, @Value("${pelias.place.boost:4}") long placeBoost) {
        this.wrapperFactory = wrapperFactory;
        this.placeBoost = placeBoost;
    }

    public Collection<ElasticsearchCommand> transform(InputStream placeNamesStream) {
        return new FeatureJSONCollection(placeNamesStream)
                .mapToList(f -> createMapper(f).toPeliasDocument()).stream()
                .filter(Objects::nonNull).map(ElasticsearchCommand::peliasIndexCommand).toList();
    }

    TopographicPlaceAdapterToPeliasDocument createMapper(SimpleFeature feature) {

        TopographicPlaceAdapter wrapper = wrapperFactory.createWrapper(feature);

        return switch (wrapper.getType()) {
            case COUNTY -> new CountyToPeliasDocument(wrapper);
            case LOCALITY -> new LocalityToPeliasDocument(wrapper);
            case BOROUGH -> new BoroughToPeliasDocument(wrapper);
            case PLACE -> new PlaceToPeliasDocument(wrapper, placeBoost);
            default -> null;
        };
    }


}
