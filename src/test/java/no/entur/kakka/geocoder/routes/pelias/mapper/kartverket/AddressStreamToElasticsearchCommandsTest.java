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


import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import no.entur.kakka.geocoder.routes.pelias.elasticsearch.ElasticsearchCommand;
import no.entur.kakka.geocoder.routes.pelias.json.AddressParts;
import no.entur.kakka.geocoder.routes.pelias.json.PeliasDocument;
import no.entur.kakka.geocoder.routes.pelias.mapper.coordinates.GeometryTransformer;
import no.entur.kakka.geocoder.routes.pelias.json.Parent;
import org.junit.Assert;
import org.junit.Test;

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


        Assert.assertEquals(28, commands.size());


        commands.forEach(c -> assertCommand(c));

        List<PeliasDocument> documents = commands.stream().map(c -> (PeliasDocument) c.getSource()).collect(Collectors.toList());
        Assert.assertEquals("Should be 8 streets", 8, documents.stream().filter(d -> PeliasDocument.DEFAULT_SOURCE.equals(d.getSource())).collect(Collectors.toList()).size());

        PeliasDocument knownDocument = commands.stream().map(c -> (PeliasDocument) c.getSource()).filter(d -> d.getSourceId().endsWith("416246828")).collect(Collectors.toList()).get(0);
        assertKnownAddress(knownDocument);
    }


    //416246828;0123;SPYDEBERG;vegadresse;;;1096;Vestlundveien;25;;30;255;;;Vestlundveien 25;Vestlundveien 25;25833;6615279.64;277680.71;1820;SPYDEBERG;01230107;LUND;02040701;Spydeberg;976985877;0101;Spydeberg;3;SPYDEBERG;22.05.2015 08:35:54;24.03.2019 08:19:38
    private void assertKnownAddress(PeliasDocument known) throws Exception {

        AddressParts addressParts = known.getAddressParts();

        Assert.assertEquals("Vestlundveien", addressParts.getStreet());
        Assert.assertEquals("25", addressParts.getNumber());
        Assert.assertEquals("1820", addressParts.getZip());
        Assert.assertEquals("Vestlundveien", addressParts.getName());


        Point utm33Point = new GeometryFactory().createPoint(new Coordinate(277680.73, 6615279.63));
        Point wgs84Point = GeometryTransformer.fromUTM(utm33Point, "33");

        Assert.assertEquals(wgs84Point.getY(), known.getCenterPoint().getLat(), 0.0001);
        Assert.assertEquals(wgs84Point.getX(), known.getCenterPoint().getLon(), 0.0001);

        Parent parent = known.getParent();
        Assert.assertEquals("NOR", parent.getCountryId());
        Assert.assertEquals("1820", parent.getPostalCodeId());
        Assert.assertEquals("01", parent.getCountyId());
        Assert.assertEquals("0123", parent.getLocalityId());
        Assert.assertEquals("01230107", parent.getBoroughId());
        Assert.assertEquals("Lund", parent.getBorough());

        Assert.assertEquals(ADDRESS_POPULARITY, known.getPopularity());

        Assert.assertEquals("Vestlundveien 25", known.getNameMap().get("default"));
        Assert.assertEquals(Arrays.asList("vegadresse"), known.getCategory());
    }

    private void assertCommand(ElasticsearchCommand command) {
        Assert.assertNotNull(command.getIndex());
        Assert.assertEquals("pelias", command.getIndex().getIndex());
        Assert.assertEquals("address", command.getIndex().getType());
    }
}
