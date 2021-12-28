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

package no.entur.kakka.geocoder.routes.pelias.mapper.kartverket;


import no.entur.kakka.geocoder.routes.pelias.elasticsearch.ElasticsearchCommand;
import no.entur.kakka.geocoder.routes.pelias.json.AddressParts;
import no.entur.kakka.geocoder.routes.pelias.json.Parent;
import no.entur.kakka.geocoder.routes.pelias.json.PeliasDocument;
import no.entur.kakka.geocoder.routes.pelias.mapper.coordinates.GeometryTransformer;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;

import java.io.FileInputStream;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

public class AddressStreamToElasticsearchCommandsTest {
    private static final Long ADDRESS_POPULARITY = 7L;
    private static final Long ADDRESS_STREET_POPULARITY = 9L;

    @Test
    public void testStreamAddressesToIndexCommands() throws Exception {
        AddressStreamToElasticSearchCommands transformer = new AddressStreamToElasticSearchCommands(new AddressToPeliasMapper(ADDRESS_POPULARITY), new AddressToStreetMapper(ADDRESS_STREET_POPULARITY));

        Collection<ElasticsearchCommand> commands = transformer
                .transform(new FileInputStream("src/test/resources/no/entur/kakka/geocoder/csv/addresses.csv"));


        Assertions.assertEquals(28, commands.size());


        commands.forEach(c -> assertCommand(c));

        List<PeliasDocument> documents = commands.stream().map(c -> (PeliasDocument) c.getSource()).collect(Collectors.toList());
        Assertions.assertEquals(8, documents.stream().filter(d -> PeliasDocument.DEFAULT_SOURCE.equals(d.getSource())).collect(Collectors.toList()).size(), "Should be 8 streets");

        PeliasDocument knownDocument = commands.stream().map(c -> (PeliasDocument) c.getSource()).filter(d -> d.getSourceId().endsWith("416246828")).collect(Collectors.toList()).get(0);
        assertKnownAddress(knownDocument);
    }


    //416246828;0123;SPYDEBERG;vegadresse;;;1096;Vestlundveien;25;;30;255;;;Vestlundveien 25;Vestlundveien 25;25833;6615279.64;277680.71;1820;SPYDEBERG;01230107;LUND;02040701;Spydeberg;976985877;0101;Spydeberg;3;SPYDEBERG;22.05.2015 08:35:54;24.03.2019 08:19:38
    private void assertKnownAddress(PeliasDocument known) throws Exception {

        AddressParts addressParts = known.getAddressParts();

        Assertions.assertEquals("Vestlundveien", addressParts.getStreet());
        Assertions.assertEquals("25", addressParts.getNumber());
        Assertions.assertEquals("1820", addressParts.getZip());
        Assertions.assertEquals("Vestlundveien", addressParts.getName());


        Point utm33Point = new GeometryFactory().createPoint(new Coordinate(277680.73, 6615279.63));
        Point wgs84Point = GeometryTransformer.fromUTM(utm33Point, "33");

        Assertions.assertEquals(wgs84Point.getY(), known.getCenterPoint().getLat(), 0.0001);
        Assertions.assertEquals(wgs84Point.getX(), known.getCenterPoint().getLon(), 0.0001);

        Parent parent = known.getParent();
        Assertions.assertEquals("1820", parent.getPostalCodeId());
        Assertions.assertEquals("KVE:TopographicPlace:0123", parent.getLocalityId());
        Assertions.assertEquals("01230107", parent.getBoroughId());
        Assertions.assertEquals("Lund", parent.getBorough());

        Assertions.assertEquals(ADDRESS_POPULARITY, known.getPopularity());

        Assertions.assertEquals("Vestlundveien 25", known.getNameMap().get("default"));
        Assertions.assertEquals(Arrays.asList("vegadresse"), known.getCategory());
    }

    private void assertCommand(ElasticsearchCommand command) {
        Assertions.assertNotNull(command.getIndex());
        Assertions.assertEquals("pelias", command.getIndex().getIndex());
        Assertions.assertEquals("address", command.getIndex().getType());
    }
}
