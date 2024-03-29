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

package no.entur.kakka.geocoder.routes.pelias.elasticsearch;


import no.entur.kakka.geocoder.routes.pelias.json.GeoPoint;
import no.entur.kakka.geocoder.routes.pelias.json.Parent;
import no.entur.kakka.geocoder.routes.pelias.json.PeliasDocument;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;

public class ElasticsearchBulkCommandWriterTest {


    @Test
    public void testWriteBulkCommandWithPeliasDocuments() throws Exception {
        List<ElasticsearchCommand> commands = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            commands.add(ElasticsearchCommand.peliasIndexCommand(doc("møre" + i)));

        }

        StringWriter writer = new StringWriter();
        new ElasticsearchBulkCommandWriter(writer).write(commands);
        String asString = writer.toString();

        Assertions.assertEquals(commands.size() * 2, StringUtils.countMatches(asString, "\n"));
        Assertions.assertTrue(asString.contains("\"name\":{\"default\":\"møre0\""));
    }


    private PeliasDocument doc(String name) {
        PeliasDocument peliasDocument = new PeliasDocument("layer", "sourceId");
        peliasDocument.setDefaultNameAndPhrase(name);
        peliasDocument.setCenterPoint(new GeoPoint(51.7651177, -0.2336668));

        peliasDocument.setParent(Parent.builder().withBorough("bor").withCountryId("NOR").build());

        return peliasDocument;
    }

}
