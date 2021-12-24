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
import no.entur.kakka.geocoder.sosi.SosiFileFilter;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.FileInputStream;
import java.util.Collection;

public class SosiFileFilterTest {

    @Test
    public void filterByType() throws Exception {
        String targetFile = "target/filtered.sos";
        new SosiFileFilter().filterElements(new FileInputStream("src/test/resources/no/entur/kakka/geocoder/sosi/placeNames.sos"), targetFile, kv -> "NAVNEOBJEKTTYPE".equals(kv.getKey()) && "industriområde".equals(kv.getValue()));

        Collection<ElasticsearchCommand> commands = new KartverketSosiStreamToElasticsearchCommands(new SosiElementWrapperFactory(), 1).transform(new FileInputStream(targetFile));

        Assertions.assertEquals(1, commands.size());

        Assertions.assertEquals(((PeliasDocument) commands.iterator().next().getSource()).getDefaultName(), "Stornesodden");
    }

}
