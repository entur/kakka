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

import no.entur.kakka.Constants;
import no.entur.kakka.geocoder.netex.osm.NetexHelper;
import no.entur.kakka.geocoder.netex.osm.OsmToNetexMapper;
import no.entur.kakka.geocoder.netex.osm.OsmUnmarshaller;
import org.apache.camel.Exchange;
import org.apache.commons.io.FilenameUtils;
import org.openstreetmap.osm.Node;
import org.openstreetmap.osm.Osm;
import org.rutebanken.netex.model.ObjectFactory;
import org.rutebanken.netex.model.PublicationDeliveryStructure;
import org.rutebanken.netex.model.SiteFrame;
import org.rutebanken.netex.model.TariffZone;
import org.rutebanken.netex.model.TariffZonesInFrame_RelStructure;
import org.rutebanken.netex.model.Zone_VersionStructure;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import javax.xml.bind.JAXBElement;
import javax.xml.bind.JAXBException;
import javax.xml.parsers.ParserConfigurationException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.List;
import java.util.stream.Collectors;


@Component
public class TariffZoneConverter {

    private final Logger logger = LoggerFactory.getLogger(getClass());


    private final NetexHelper netexHelper;


    public TariffZoneConverter() throws JAXBException, IOException, SAXException {

        this.netexHelper = new NetexHelper(new ObjectFactory());
    }

    public void toNetexFile(Exchange e, String localWorkingDir) throws JAXBException, IOException, SAXException, ParserConfigurationException, ClassNotFoundException {

        String fileName = e.getIn().getHeader(Constants.FILE_NAME, String.class);

        logger.info("Converting to tariff zone: {}", fileName);


        OsmUnmarshaller osmUnmarshaller = new OsmUnmarshaller(false);

        final File file = new File(localWorkingDir + "/" + fileName);


        final String netexFileName = FilenameUtils.getBaseName(file.getName()) + ".xml";
        var netexOutputFile = localWorkingDir + "/" + netexFileName;

        var inputStream = new FileInputStream(file);
        var inputStreamReader = new InputStreamReader(inputStream);
        var inputSource = new InputSource(inputStreamReader);

        Osm osm = osmUnmarshaller.unmarshall(inputSource);

        logger.info("Unmarshalled OSM file. generator: {}, version: {}, nodes: {}, ways: {}",
                osm.getGenerator(), osm.getVersion(), osm.getNode().size(), osm.getWay().size());

        SiteFrame siteFrame = netexHelper.createSiteFrame();

        map(osm, siteFrame);

        PublicationDeliveryStructure publicationDeliveryStructure = netexHelper.createPublicationDelivery(siteFrame, file.getName());

        FileOutputStream fileOutputStream = new FileOutputStream(netexOutputFile);

        netexHelper.marshalNetex(publicationDeliveryStructure, fileOutputStream);

        logger.debug("Converted osm to netex file {}", netexOutputFile);

        var providerId = e.getIn().getHeader("providerId", String.class);

        e.getIn().setHeader(Constants.FILE_NAME, netexFileName);
        e.getIn().setHeader(Constants.FILE_HANDLE, "tariffzones/netex/" + providerId + "/" + netexFileName);
        e.getIn().setBody(new File(netexOutputFile));
    }

    private void map(Osm osm, SiteFrame siteFrame) {

        var mapOfNodes = osm.getNode().stream().collect(Collectors.toMap(Node::getId, node -> node));
        logger.info("Mapped {} nodes from osm file", mapOfNodes.size());

        OsmToNetexMapper<TariffZone> osmToNetexMapper = new OsmToNetexMapper<>(netexHelper);
        List<TariffZone> tariffZones = osmToNetexMapper.mapWaysToZoneList(osm.getWay(), mapOfNodes, TariffZone.class);

        List<JAXBElement<? extends Zone_VersionStructure>> tariffZones1 = tariffZones.stream()
                .map(tariffZone -> new ObjectFactory().createTariffZone(tariffZone)).collect(Collectors.toList());
        siteFrame.withTariffZones(
                new TariffZonesInFrame_RelStructure()
                        .withTariffZone(tariffZones1));

    }
}
