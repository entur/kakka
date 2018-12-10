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

package no.entur.kakka.geocoder.netex.pbf;

import net.opengis.gml._3.PolygonType;
import no.entur.kakka.geocoder.netex.NetexGeoUtil;
import no.entur.kakka.geocoder.netex.TopographicPlaceNetexWriter;
import no.entur.kakka.openstreetmap.OpenStreetMapContentHandler;
import no.entur.kakka.openstreetmap.model.OSMNode;
import no.entur.kakka.openstreetmap.model.OSMWay;
import no.entur.kakka.openstreetmap.model.OSMWithTags;
import org.apache.commons.lang3.StringUtils;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygon;
import org.rutebanken.netex.model.CountryRef;
import org.rutebanken.netex.model.IanaCountryTldEnumeration;
import org.rutebanken.netex.model.KeyListStructure;
import org.rutebanken.netex.model.KeyValueStructure;
import org.rutebanken.netex.model.LocationStructure;
import org.rutebanken.netex.model.ModificationEnumeration;
import org.rutebanken.netex.model.MultilingualString;
import org.rutebanken.netex.model.SimplePoint_VersionStructure;
import org.rutebanken.netex.model.TopographicPlace;
import org.rutebanken.netex.model.TopographicPlaceDescriptor_VersionedChildStructure;
import org.rutebanken.netex.model.TopographicPlaceTypeEnumeration;
import org.rutebanken.netex.model.TopographicPlace_VersionStructure;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.CollectionUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.stream.Collectors;

/**
 * Map OSM nodes and ways to Netex topographic place.
 * <p>
 * Ways refer to nodes for coordinates. Because of this files must be parsed twice,
 * first to collect nodes ids referred by relevant ways and then to map relevant nodes and ways.
 */
public class TopographicPlaceOsmContentHandler implements OpenStreetMapContentHandler {
    private static final Logger logger = LoggerFactory.getLogger(TopographicPlaceNetexWriter.class);

    private BlockingQueue<TopographicPlace> topographicPlaceQueue;

    private List<String> tagFilters;

    private String participantRef;

    private IanaCountryTldEnumeration countryRef;

    private static final String TAG_NAME = "name";

    private Map<Long, OSMNode> nodes = new HashMap<>();

    private Set<Long> nodeRefsUsedInWays = new HashSet<>();

    private boolean gatherNodesUsedInWaysPhase = true;

    public TopographicPlaceOsmContentHandler(BlockingQueue<TopographicPlace> topographicPlaceQueue,
                                                    List<String> tagFilters, String participantRef, IanaCountryTldEnumeration countryRef) {
        this.topographicPlaceQueue = topographicPlaceQueue;
        this.tagFilters = cleanFilter(tagFilters);
        this.participantRef = participantRef;
        this.countryRef = countryRef;
    }

    private List<String> cleanFilter(List<String> rawFilter) {
        if (CollectionUtils.isEmpty(rawFilter)) {
            return new ArrayList<>();
        }
        return rawFilter.stream().filter(f -> !StringUtils.isEmpty(f)).map(String::trim).collect(Collectors.toList());
    }

    @Override
    public void addNode(OSMNode osmNode) {
        if (matchesFilter(osmNode)) {
            TopographicPlace topographicPlace = map(osmNode).withCentroid(toCentroid(osmNode.lat, osmNode.lon));
            topographicPlaceQueue.add(topographicPlace);
        }

        if (nodeRefsUsedInWays.contains(osmNode.getId())) {
            nodes.put(osmNode.getId(), osmNode);
        }
    }

    @Override
    public void addWay(OSMWay osmWay) {
        if (matchesFilter(osmWay)) {

            if (gatherNodesUsedInWaysPhase) {
                nodeRefsUsedInWays.addAll(osmWay.getNodeRefs());
            } else {
                TopographicPlace topographicPlace = map(osmWay);
                if (addGeometry(osmWay, topographicPlace)) {
                    topographicPlaceQueue.add(topographicPlace);
                }
            }
        }
    }

    private boolean addGeometry(OSMWay osmWay, TopographicPlace topographicPlace) {
        List<Coordinate> coordinates = new ArrayList<>();
        for (Long nodeRef : osmWay.getNodeRefs()) {
            OSMNode node = nodes.get(nodeRef);
            if (node != null) {
                coordinates.add(new Coordinate(node.lon, node.lat));
            }
        }

        if (coordinates.size() != osmWay.getNodeRefs().size()) {
            logger.info("Ignoring osmWay with missing nodes: " + osmWay.getAssumedName());
            return false;
        }
        topographicPlace.withCentroid(toCentroid(coordinates));
        try {
            topographicPlace.withPolygon(toPolygon(coordinates, participantRef + "-" + osmWay.getId()));
        } catch (RuntimeException e) {
            logger.info("Could not create polygon for osm way: " + osmWay.getAssumedName() + ". Exception: " + e.getMessage());
        }
        return true;
    }

    @Override
    public void doneSecondPhaseWays() {
        gatherNodesUsedInWaysPhase = false;
    }

    boolean matchesFilter(OSMWithTags entity) {
        if (!entity.hasTag(TAG_NAME)) {
            return false;
        }

        for (Map.Entry<String, String> tag : entity.getTags().entrySet()) {
            if (tagFilters.stream().anyMatch(f -> (tag.getKey() + "=" + tag.getValue()).startsWith(f))) {
                return true;
            }
        }
        return false;
    }

    TopographicPlace map(OSMWithTags entity) {
        return new TopographicPlace()
                       .withVersion("any")
                       .withModification(ModificationEnumeration.NEW)
                       .withName(multilingualString(entity.getAssumedName()))
                       .withAlternativeDescriptors(mapAlternativeDescriptors(entity))
                       .withDescriptor(new TopographicPlaceDescriptor_VersionedChildStructure().withName(multilingualString(entity.getAssumedName())))
                       .withTopographicPlaceType(TopographicPlaceTypeEnumeration.PLACE_OF_INTEREST)
                       .withCountryRef(new CountryRef().withRef(countryRef))
                       .withId(prefix(entity.getId()))
                       .withKeyList(new KeyListStructure().withKeyValue(mapKeyValues(entity)));
    }

    TopographicPlace_VersionStructure.AlternativeDescriptors mapAlternativeDescriptors(OSMWithTags entity) {

        List<TopographicPlaceDescriptor_VersionedChildStructure> descriptors = entity.getTags().entrySet().stream().filter(e -> !e.getKey().equals("name") && e.getKey().startsWith("name:") && e.getValue() != null)
                                                                                       .map(e -> new TopographicPlaceDescriptor_VersionedChildStructure()
                                                                                                         .withName(new MultilingualString().withValue(e.getValue()).withLang(e.getKey().replaceFirst("name:", "")))).collect(Collectors.toList());

        if (descriptors.size() == 0) {
            return null;
        }

        return new TopographicPlace_VersionStructure.AlternativeDescriptors().withTopographicPlaceDescriptor(descriptors);
    }

    List<KeyValueStructure> mapKeyValues(OSMWithTags entity) {
        return entity.getTags().entrySet().stream()
                       .filter(e -> !TAG_NAME.equals(e.getKey()))
                       .map(e -> new KeyValueStructure().withKey(e.getKey()).withValue(e.getValue()))
                       .collect(Collectors.toList());
    }

    protected String prefix(long id) {
        return participantRef + ":TopographicPlace:" + id;
    }


    protected MultilingualString multilingualString(String val) {
        return new MultilingualString().withLang("no").withValue(val);
    }


    private PolygonType toPolygon(List<Coordinate> coordinates, String id) {
        Polygon polygon = new GeometryFactory().createPolygon(coordinates.toArray(new Coordinate[coordinates.size()]));
        return NetexGeoUtil.toNetexPolygon(polygon).withId(id);
    }


    /**
     * Calculate centroid of list of coordinates.
     * <p>
     * If coordinates may be converted to a polygon, the polygons centroid is used. If not, the centroid of the corresponding multipoint is used.
     *
     * @param coordinates
     * @return
     */
    SimplePoint_VersionStructure toCentroid(List<Coordinate> coordinates) {
        Point centroid;
        try {
            centroid = new GeometryFactory().createPolygon(coordinates.toArray(new Coordinate[coordinates.size()])).getCentroid();
        } catch (RuntimeException re) {
            centroid = new GeometryFactory().createMultiPoint(coordinates.toArray(new Coordinate[coordinates.size()])).getCentroid();
        }
        return toCentroid(centroid.getY(), centroid.getX());
    }


    SimplePoint_VersionStructure toCentroid(double latitude, double longitude) {
        return new SimplePoint_VersionStructure().withLocation(
                new LocationStructure().withLatitude(new BigDecimal(latitude))
                        .withLongitude(new BigDecimal(longitude)));
    }


}
