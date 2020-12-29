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

import net.opengis.gml._3.AbstractRingPropertyType;
import net.opengis.gml._3.DirectPositionListType;
import net.opengis.gml._3.LinearRingType;
import net.opengis.gml._3.PolygonType;

import org.openstreetmap.osm.Node;
import org.openstreetmap.osm.Tag;
import org.openstreetmap.osm.Way;
import org.rutebanken.netex.model.KeyListStructure;
import org.rutebanken.netex.model.KeyValueStructure;
import org.rutebanken.netex.model.MultilingualString;
import org.rutebanken.netex.model.Zone_VersionStructure;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigInteger;
import java.util.List;

import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Map osm Ways to Netex Zones
 *
 * @param <T>
 */
public class OsmToNetexMapper<T extends Zone_VersionStructure> {

    /**
     * The prefix (usually three letters) in IDs.
     */
    public static final String CODESPACE = "codespace";
    /**
     * The objects name that will be retrieved from a tag with the same value.
     */
    public static final String NAME = "name";
    /**
     * Reference, which is the postfix of the generated Netex ID
     */
    public static final String REFERENCE = "reference";
    public static final String ZONE_TYPE = "zone_type";
    public static final String DEFAULT_VERSION = "1";
    private static final Logger logger = LoggerFactory.getLogger(OsmToNetexTransformer.class);
    private static final net.opengis.gml._3.ObjectFactory openGisObjectFactory = new net.opengis.gml._3.ObjectFactory();
    private final NetexHelper netexHelper;

    public OsmToNetexMapper(NetexHelper netexHelper) {
        this.netexHelper = netexHelper;
    }

    public List<T> mapWaysToZoneList(List<Way> ways, Map<BigInteger, Node> mapOfNodes, Class<T> clazz) {
        List<T> zones = ways
                .stream()
                .map(way -> mapWayToZone(way, mapOfNodes, clazz))
                .collect(Collectors.toList());
        logger.info("Mapped {} zones of type {} from osm to netex", zones.size(), clazz);
        return zones;
    }

    public T mapWayToZone(Way way, Map<BigInteger, Node> mapOfNodes, Class<T> clazz) {

        T zone = netexHelper.createNetexObject(clazz);

        zone.setVersion(DEFAULT_VERSION);

        mapTags(way.getTag(), zone, clazz.getSimpleName());

        zone.setPolygon(mapNodes(way, mapOfNodes));

        return zone;
    }

    private PolygonType mapNodes(Way way, Map<BigInteger, Node> mapOfNodes) {

        List<Double> coordinates = way.getNd().stream()
                .map(nd -> mapOfNodes.get(nd.getRef()))
                .flatMap(node -> Stream.of(node.getLat(), node.getLon()))
                .collect(Collectors.toList());

        AbstractRingPropertyType abstractRingPropertyType = new AbstractRingPropertyType()
                .withAbstractRing(openGisObjectFactory.createLinearRing(
                        new LinearRingType()
                                .withPosList(
                                        new DirectPositionListType().withValue(coordinates))));

        return new PolygonType()
                .withId("GEN-PolygonType" + way.getId())
                .withExterior(abstractRingPropertyType);
    }

    private void mapTags(List<Tag> tags, Zone_VersionStructure zone, String className) {
        String codespace = null;
        String reference = null;

        for (Tag tag : tags) {

            if (tag.getK().equals(CODESPACE)) {
                codespace = tag.getV();
            } else if (tag.getK().startsWith(NAME)) {
                String keyName = tag.getK();
                String lang = extractLangFromNameTagKey(keyName);
                zone.setName(new MultilingualString().withValue(tag.getV()).withLang(lang));
            } else if (tag.getK().startsWith(REFERENCE)) {
                reference = tag.getV();
            } else if (tag.getK().startsWith(ZONE_TYPE)) {
                String keyName = tag.getK();
                String value = tag.getV();
                tagValueNotNull(ZONE_TYPE,value);

                KeyValueStructure keyValueStructure = new KeyValueStructure().withKey(keyName).withValue(value);
                KeyListStructure keyListStructure = new KeyListStructure().withKeyValue(keyValueStructure);
                zone.setKeyList(keyListStructure);
            }
        }

        tagValueNotNull(CODESPACE, codespace);
        tagValueNotNull(REFERENCE, reference);


        zone.setId(generateId(codespace, className, reference));
    }

    private String generateId(String codespace, String className, String reference) {
        return codespace + ":" + className + ":" + reference;
    }

    private String extractLangFromNameTagKey(String osmTagName) {
        return osmTagName.substring(osmTagName.lastIndexOf(':') + 1);
    }

    private void tagValueNotNull(String name, String value) {

        if (value == null) {
            throw new IllegalArgumentException("Cannot map '" + name + "' from tag. Value not present. Seems like there are no tags with name " + name);
        }
    }

}
