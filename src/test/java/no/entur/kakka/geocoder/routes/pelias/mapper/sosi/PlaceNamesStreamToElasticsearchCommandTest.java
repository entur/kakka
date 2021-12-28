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

package no.entur.kakka.geocoder.routes.pelias.mapper.sosi;

import no.entur.kakka.geocoder.routes.pelias.elasticsearch.ElasticsearchCommand;
import no.entur.kakka.geocoder.routes.pelias.json.PeliasDocument;
import no.entur.kakka.geocoder.routes.pelias.mapper.kartverket.KartverketSosiStreamToElasticsearchCommands;
import no.entur.kakka.geocoder.sosi.SosiElementWrapperFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.FileInputStream;
import java.util.Arrays;
import java.util.Collection;
import java.util.stream.Collectors;

public class PlaceNamesStreamToElasticsearchCommandTest {


    private static final Long placePopularity = 3l;

    private final SosiElementWrapperFactory sosiElementWrapperFactory = new SosiElementWrapperFactory();


    @Test
    public void testTransform() throws Exception {
        Collection<ElasticsearchCommand> commands = new KartverketSosiStreamToElasticsearchCommands(sosiElementWrapperFactory, placePopularity).transform(new FileInputStream("src/test/resources/no/entur/kakka/geocoder/sosi/placeNames.sos"));

        Assertions.assertEquals(2, commands.size());

        commands.forEach(c -> assertCommand(c));

        PeliasDocument kalland = commands.stream().map(c -> (PeliasDocument) c.getSource()).filter(d -> "Stornesodden".equals(d.getDefaultName())).collect(Collectors.toList()).get(0);
        assertKalland(kalland);
    }

    private void assertKalland(PeliasDocument kalland) {
        Assertions.assertEquals(58.71085, kalland.getCenterPoint().getLat().doubleValue(), 0.0001);
        Assertions.assertEquals(7.397255, kalland.getCenterPoint().getLon().doubleValue(), 0.0001);

        Assertions.assertEquals("NOR", kalland.getParent().getCountryId());
        Assertions.assertEquals(Arrays.asList("industriområde"), kalland.getCategory());
        Assertions.assertEquals(placePopularity, kalland.getPopularity());
    }

    private void assertCommand(ElasticsearchCommand command) {
        Assertions.assertNotNull(command.getIndex());
        Assertions.assertEquals("pelias", command.getIndex().getIndex());
        Assertions.assertEquals("address", command.getIndex().getType());
    }

}
