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


import no.entur.kakka.geocoder.geojson.GeojsonFeatureWrapperFactory;
import no.entur.kakka.geocoder.routes.pelias.elasticsearch.ElasticsearchCommand;
import no.entur.kakka.geocoder.routes.pelias.json.PeliasDocument;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.FileInputStream;
import java.util.Collection;

public class FylkeToElasticsearchCommandTest {

    @Test
    public void testTransform() throws Exception {
        KartverketGeoJsonStreamToElasticsearchCommands transformer = new KartverketGeoJsonStreamToElasticsearchCommands(new GeojsonFeatureWrapperFactory(null), 1);
        Collection<ElasticsearchCommand> commands = transformer
                .transform(new FileInputStream("src/test/resources/no/entur/kakka/geocoder/geojson/fylker.geojson"));

        Assertions.assertEquals(4, commands.size());

        commands.forEach(this::assertCommand);

        PeliasDocument kalland = commands.stream().map(c -> (PeliasDocument) c.getSource()).filter(d -> "Buskerud".equals(d.getDefaultName())).toList().getFirst();
        assertBuskerud(kalland);
    }

    private void assertBuskerud(PeliasDocument buskerud) {
        Assertions.assertNotNull(buskerud.getShape());
        Assertions.assertEquals("NOR", buskerud.getParent().getCountryId());
        Assertions.assertEquals("06", buskerud.getSourceId());
    }

    private void assertCommand(ElasticsearchCommand command) {
        Assertions.assertNotNull(command.getIndex());
        Assertions.assertEquals("pelias", command.getIndex().getIndex());
        Assertions.assertEquals("county", command.getIndex().getType());
    }

}
