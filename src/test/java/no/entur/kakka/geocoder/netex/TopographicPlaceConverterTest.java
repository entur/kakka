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

package no.entur.kakka.geocoder.netex;


import no.entur.kakka.domain.OSMPOIFilter;
import no.entur.kakka.geocoder.featurejson.FeatureJSONFilter;
import no.entur.kakka.geocoder.geojson.GeojsonFeatureWrapperFactory;
import no.entur.kakka.geocoder.netex.geojson.GeoJsonCollectionTopographicPlaceReader;
import no.entur.kakka.geocoder.netex.geojson.GeoJsonSingleTopographicPlaceReader;
import no.entur.kakka.geocoder.netex.pbf.PbfTopographicPlaceReader;
import no.entur.kakka.geocoder.netex.sosi.SosiTopographicPlaceReader;
import no.entur.kakka.geocoder.sosi.SosiElementWrapperFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.rutebanken.netex.model.IanaCountryTldEnumeration;
import org.rutebanken.netex.model.PublicationDeliveryStructure;
import org.rutebanken.netex.model.Site_VersionFrameStructure;
import org.rutebanken.netex.validation.NeTExValidator;
import org.springframework.util.CollectionUtils;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.Unmarshaller;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamReader;
import java.io.File;
import java.io.FileInputStream;
import java.util.Arrays;
import java.util.List;

import static javax.xml.bind.JAXBContext.newInstance;

public class TopographicPlaceConverterTest {

    private final TopographicPlaceConverter converter = new TopographicPlaceConverter("CET");

    @Test
    public void testFilterConvertAdminUnitsFromGeoJson() throws Exception {
        String filteredFilePath = "target/filtered-fylker.geojson";
        new FeatureJSONFilter("src/test/resources/no/entur/kakka/geocoder/geojson/fylker.geojson", filteredFilePath, "fylkesnr", "area").filter();

        String targetPath = "target/adm-units-from-geojson.xml";
        converter.toNetexFile(new GeoJsonCollectionTopographicPlaceReader
                (new GeojsonFeatureWrapperFactory(null), new File(filteredFilePath)
                ), targetPath);
        validateNetexFile(targetPath);
    }


    @Test
    public void testConvertPlaceOfInterestFromOsmPbf() throws Exception {
        List<OSMPOIFilter> filters = Arrays.asList(createFilter("leisure", "common"), createFilter("naptan:indicator", ""));
        TopographicPlaceReader reader = new PbfTopographicPlaceReader(filters, IanaCountryTldEnumeration.NO,
                new File("src/test/resources/no/entur/kakka/geocoder/pbf/sample.pbf"));
        String targetPath = "target/poi.xml";
        converter.toNetexFile(reader,
                targetPath);

        validateNetexFile(targetPath);
    }

    @Test
    public void testConvertAdminUnitsFromSosi() throws Exception {
        TopographicPlaceReader reader = new SosiTopographicPlaceReader(new SosiElementWrapperFactory(), Arrays.asList(new File("src/test/resources/no/entur/kakka/geocoder/sosi/SosiTest.sos")));
        String targetPath = "target/admin-units-from-sosi.xml";
        converter.toNetexFile(reader,
                targetPath);

        validateNetexFile(targetPath);
    }


    @Test
    public void testConvertNeighbouringCountriesFromGeoJson() throws Exception {
        TopographicPlaceReader reader = new GeoJsonSingleTopographicPlaceReader(new GeojsonFeatureWrapperFactory(null),
                new File("src/test/resources/no/entur/kakka/geocoder/geojson/finland.geojson"));
        String targetPath = "target/neighbouring-countries_from_geosjon.xml";
        converter.toNetexFile(reader,
                targetPath);

        validateNetexFile(targetPath);
    }

//    @Test // File is to big for source control
//    public void testConvertAdminUnitsFromSosiRealKartverketData() throws Exception {
//        TopographicPlaceReader reader = new SosiTopographicPlaceReader(new File("files/ADM_enheter_Norge.sos"));
//        String targetPath = "target/admin-units-from-sosi.xml";
//        converter.toNetexFile(reader,
//                targetPath);
//
//        validateNetexFile(targetPath);
//    }


    private PublicationDeliveryStructure validateNetexFile(String path) throws Exception {
        JAXBContext publicationDeliveryContext = newInstance(PublicationDeliveryStructure.class);
        Unmarshaller unmarshaller = publicationDeliveryContext.createUnmarshaller();

        NeTExValidator neTExValidator = new NeTExValidator();
        unmarshaller.setSchema(neTExValidator.getSchema());
        XMLStreamReader xmlReader = XMLInputFactory.newInstance().createXMLStreamReader(new FileInputStream(new File(path)));
        JAXBElement<PublicationDeliveryStructure> jaxbElement = unmarshaller.unmarshal(xmlReader, PublicationDeliveryStructure.class);
        PublicationDeliveryStructure publicationDeliveryStructure = jaxbElement.getValue();

        boolean containsTopographicPlaces = publicationDeliveryStructure.getDataObjects().getCompositeFrameOrCommonFrame().stream().map(frame -> frame.getValue())
                .filter(frame -> frame instanceof Site_VersionFrameStructure).anyMatch(frame -> ((Site_VersionFrameStructure) frame).getTopographicPlaces() != null && !CollectionUtils.isEmpty(((Site_VersionFrameStructure) frame).getTopographicPlaces().getTopographicPlace()));

        Assertions.assertTrue(containsTopographicPlaces, "Expected publication delivery to contain site frame with topograhpic places");
        return publicationDeliveryStructure;
    }

    private OSMPOIFilter createFilter(String key, String value) {
        return OSMPOIFilter.fromKeyAndValue(key, value);
    }
}
