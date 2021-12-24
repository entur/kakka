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

package no.entur.kakka.geocoder.routes.pelias.mapper;

import no.entur.kakka.geocoder.routes.pelias.json.Parent;
import org.junit.jupiter.api.BeforeEach;
import org.locationtech.jts.geom.Point;
import no.entur.kakka.geocoder.geojson.KartverketLocality;
import no.entur.kakka.geocoder.routes.pelias.json.GeoPoint;
import no.entur.kakka.geocoder.routes.pelias.elasticsearch.ElasticsearchCommand;
import no.entur.kakka.geocoder.routes.pelias.json.PeliasDocument;
import no.entur.kakka.geocoder.services.AdminUnitRepository;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

public class PeliasIndexParentInfoEnricherTest {

    @Mock
    private AdminUnitRepository adminUnitRepository;

    @Mock
    private KartverketLocality locality;

    private PeliasIndexParentInfoEnricher parentInfoEnricher = new PeliasIndexParentInfoEnricher();


    @BeforeEach
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void testAddParentInfoByReverseGeoLookup() {
        PeliasDocument doc = new PeliasDocument("l", "sid");
        doc.setCenterPoint(new GeoPoint(1.0, 2.0));
        ElasticsearchCommand command = ElasticsearchCommand.peliasIndexCommand(doc);

        Mockito.when(adminUnitRepository.getLocality(Mockito.any(Point.class))).thenReturn(locality);
        Mockito.when(locality.getId()).thenReturn("0103");
        Mockito.when(locality.getParentId()).thenReturn("01");

        Mockito.when(adminUnitRepository.getAdminUnitName("0103")).thenReturn("GokkLocality");
        Mockito.when(adminUnitRepository.getAdminUnitName("01")).thenReturn("GokkCounty");

        parentInfoEnricher.addMissingParentInfo(command, adminUnitRepository);

        Assertions.assertEquals("GokkCounty", doc.getParent().getCounty());
        Assertions.assertEquals("GokkLocality", doc.getParent().getLocality());
    }

    @Test
    public void testAddParentInfoByIdLookup() {
        PeliasDocument doc = new PeliasDocument("l", "sid");
        Parent parent=new Parent();
        parent.setLocalityId("0101");
        doc.setParent(parent);
        ElasticsearchCommand command = ElasticsearchCommand.peliasIndexCommand(doc);

        Mockito.when(adminUnitRepository.getAdminUnitName(parent.getLocalityId())).thenReturn("GokkLocality");

        parentInfoEnricher.addMissingParentInfo(command, adminUnitRepository);

        Assertions.assertEquals("GokkLocality", doc.getParent().getLocality());
    }


    @Test
    public void testAddParentInfoLookupByReverseGeoLookupIfIdIsUnknown() {
        PeliasDocument doc = new PeliasDocument("l", "sid");
        Parent parent=new Parent();
        parent.setLocalityId("unknownId");
        doc.setParent(parent);
        doc.setCenterPoint(new GeoPoint(1.0, 2.0));
        ElasticsearchCommand command = ElasticsearchCommand.peliasIndexCommand(doc);

        Mockito.when(adminUnitRepository.getLocality(Mockito.any(Point.class))).thenReturn(locality);

        Mockito.when(locality.getId()).thenReturn("0103");
        Mockito.when(locality.getParentId()).thenReturn("01");

        Mockito.when(adminUnitRepository.getAdminUnitName("0103")).thenReturn("GokkLocality");
        Mockito.when(adminUnitRepository.getAdminUnitName("01")).thenReturn("GokkCounty");

        parentInfoEnricher.addMissingParentInfo(command, adminUnitRepository);

        Assertions.assertEquals("GokkCounty", doc.getParent().getCounty());
        Assertions.assertEquals("GokkLocality", doc.getParent().getLocality());
    }

}
