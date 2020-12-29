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
 */

package no.entur.kakka.geocoder.netex.osm;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.openstreetmap.osm.Node;
import org.openstreetmap.osm.Osm;
import org.rutebanken.netex.model.PublicationDeliveryStructure;
import org.rutebanken.netex.model.SiteFrame;
import org.rutebanken.netex.model.StopPlace;
import org.rutebanken.netex.model.TariffZone;
import org.rutebanken.netex.model.TariffZonesInFrame_RelStructure;
import org.rutebanken.netex.model.TopographicPlace;
import org.rutebanken.netex.model.TopographicPlaceDescriptor_VersionedChildStructure;
import org.rutebanken.netex.model.TopographicPlacesInFrame_RelStructure;
import org.rutebanken.netex.model.Zone_VersionStructure;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import javax.xml.bind.JAXBException;
import javax.xml.parsers.ParserConfigurationException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

public class OsmToNetexTransformer {

    private static final Logger logger = LoggerFactory.getLogger(OsmToNetexTransformer.class);


    private final NetexHelper netexHelper;
    private final String targetEntity;

    public OsmToNetexTransformer(NetexHelper netexHelper, String targetEntity) {
        this.netexHelper = netexHelper;
        this.targetEntity = targetEntity;
    }

    public void transform(File[] files, String localWorkingDir) throws JAXBException, IOException, ClassNotFoundException, SAXException, ParserConfigurationException {

        OsmUnmarshaller osmUnmarshaller = new OsmUnmarshaller(false);

        for (File file : files) {
            var timeStamp = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
            var netexOutputFile = localWorkingDir +"/" + FilenameUtils.getBaseName(file.getName())+ "-" + timeStamp + ".xml";

            var inputStream= new FileInputStream(file);
            var inputStreamReader = new InputStreamReader(inputStream);
            var inputSource = new InputSource(inputStreamReader);

            Osm osm = osmUnmarshaller.unmarshall(inputSource);

            logger.info("Unmarshalled OSM file. generator: {}, version: {}, nodes: {}, ways: {}",
                    osm.getGenerator(), osm.getVersion(), osm.getNode().size(), osm.getWay().size());

            SiteFrame siteFrame = netexHelper.createSiteFrame();
            Class<? extends Zone_VersionStructure> clazz = validateAndGetDestinationClass(targetEntity);

            map(osm, siteFrame, clazz);

            PublicationDeliveryStructure publicationDeliveryStructure = netexHelper.createPublicationDelivery(siteFrame, file.getName());

            FileOutputStream fileOutputStream = new FileOutputStream(netexOutputFile);
            netexHelper.marshalNetex(publicationDeliveryStructure, fileOutputStream);

            logger.debug("Converted osm to netex file {}", netexOutputFile);

        }










    }


    public void map(Osm osm, SiteFrame siteFrame, Class<? extends Zone_VersionStructure> clazz) {

        var mapOfNodes = osm.getNode().stream().collect(Collectors.toMap(Node::getId, node -> node));
        logger.info("Mapped {} nodes from osm file", mapOfNodes.size());

        if (clazz.isAssignableFrom(TariffZone.class)) {
            OsmToNetexMapper<TariffZone> osmToNetexMapper = new OsmToNetexMapper<>(netexHelper);
            List<TariffZone> tariffZones = osmToNetexMapper.mapWaysToZoneList(osm.getWay(), mapOfNodes, TariffZone.class);
            siteFrame.withTariffZones(
                    new TariffZonesInFrame_RelStructure()
                            .withTariffZone(tariffZones));
        } else {
            throw new IllegalArgumentException(clazz + " is not supported");
        }
    }

    @SuppressWarnings("unchecked")
    private Class<? extends Zone_VersionStructure> validateAndGetDestinationClass(String className) throws ClassNotFoundException {
        Class<?> clazz = Class.forName(StopPlace.class.getPackage().getName() + "." + className);
        if (!Zone_VersionStructure.class.isAssignableFrom(clazz)) {
            throw new IllegalArgumentException("The class specified:" + className + ", is not a Zone !");
        }
        return (Class<? extends Zone_VersionStructure>) clazz;
    }


}
