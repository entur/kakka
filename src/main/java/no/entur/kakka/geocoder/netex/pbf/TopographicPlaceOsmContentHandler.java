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

import com.google.common.collect.ArrayListMultimap;
import net.opengis.gml._3.PolygonType;
import no.entur.kakka.domain.OSMPOIFilter;
import no.entur.kakka.geocoder.netex.NetexGeoUtil;
import no.entur.kakka.openstreetmap.OpenStreetMapContentHandler;
import no.entur.kakka.openstreetmap.Ring;
import no.entur.kakka.openstreetmap.model.OSMNode;
import no.entur.kakka.openstreetmap.model.OSMRelation;
import no.entur.kakka.openstreetmap.model.OSMRelationMember;
import no.entur.kakka.openstreetmap.model.OSMWay;
import no.entur.kakka.openstreetmap.model.OSMWithTags;
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

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
    private static final Logger logger = LoggerFactory.getLogger(TopographicPlaceOsmContentHandler.class);
    private static final String TAG_NAME = "name";
    private static final double MINIMUM_DISTANCE = 0.0002; // Minimum distance between outer polygons/rings in a relations, distance unit is min/sec
    private final BlockingQueue<TopographicPlace> topographicPlaceQueue;
    private final List<OSMPOIFilter> osmPoiFilters;
    private final String participantRef;
    private final IanaCountryTldEnumeration countryRef;
    private final Map<Long, OSMNode> nodes = new HashMap<>();

    private final Set<Long> nodeRefsUsedInWays = new HashSet<>();

    private final Set<Long> nodeRefsUsedInRel = new HashSet<>();

    private final Set<Long> relationWayIds = new HashSet<>();

    private final Map<Long, OSMRelation> relationsById = new HashMap<>();
    private final Map<Long, OSMWay> waysById = new HashMap<>();
    private final Map<Long, OSMNode> nodesById = new HashMap<>();

    private boolean gatherNodesUsedInWaysPhase = true;

    public TopographicPlaceOsmContentHandler(BlockingQueue<TopographicPlace> topographicPlaceQueue,
                                             List<OSMPOIFilter> osmPoiFilters,
                                             String participantRef,
                                             IanaCountryTldEnumeration countryRef) {
        this.topographicPlaceQueue = topographicPlaceQueue;
        this.osmPoiFilters = osmPoiFilters;
        this.participantRef = participantRef;
        this.countryRef = countryRef;
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

        if (nodesById.containsKey(osmNode.getId())) {
            return;
        }

        if (nodeRefsUsedInRel.contains(osmNode.getId())) {
            nodesById.put(osmNode.getId(), osmNode);
        }

      if (nodesById.size() % 100000 == 0) {
            logger.debug(String.format("nodes=%d", nodesById.size()));
        }
    }

    @Override
    public void addWay(OSMWay osmWay) {
        var wayId = osmWay.getId();
        if (waysById.containsKey(wayId)) {
            return;
        }
        if (relationWayIds.contains(wayId)) {
            waysById.put(wayId, osmWay);
            nodeRefsUsedInRel.addAll(osmWay.getNodeRefs());
        }

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

    @Override
    public void addRelation(OSMRelation osmRelation) {
        if (!relationsById.containsKey(osmRelation.getId()) && osmRelation.isTag("type", "multipolygon") && matchesFilter(osmRelation)) {
            var members = osmRelation.getMembers();
            members.forEach(member -> {
                if (member.getType().equals("way")) {
                    relationWayIds.add(member.getRef());
                }
            });
            relationsById.put(osmRelation.getId(), osmRelation);
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

    private boolean addGeometry(ArrayList<OSMWay> innerWays, ArrayList<OSMWay> outerWays, TopographicPlace topographicPlace) {
        List<Polygon> polygons = new ArrayList<>();


        final List<List<Long>> outerRingNodes = constructRings(outerWays);
        final List<List<Long>> innerRingNodes = constructRings(innerWays);

        var outerPolygons = outerRingNodes.stream()
                .map(ring -> new Ring(ring, nodesById).getPolygon())
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        var innerPolygons = innerRingNodes.stream()
                .map(ring -> new Ring(ring, nodesById).getPolygon())
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        if (!outerPolygons.isEmpty() && !checkPolygonProximity(outerPolygons)) {
            polygons.addAll(outerPolygons);
            polygons.addAll(innerPolygons);

            try {
                var multiPolygon = new GeometryFactory().createMultiPolygon(polygons.toArray(new Polygon[polygons.size()]));
                var interiorPoint = multiPolygon.getInteriorPoint();
                topographicPlace.withCentroid(toCentroid(interiorPoint.getY(), interiorPoint.getX()));
                return true;
            } catch (RuntimeException e) {
                logger.warn("unable to add geometry" + e);
                return false;
            }
        }
        return false;
    }

    private boolean checkPolygonProximity(List<Polygon> outerPolygons) {
        boolean outerIgnorePolygons = false;
        boolean innerIgnorePolygons = false;
        for (var p : outerPolygons) {
            for (var q : outerPolygons) {
                if (!p.isWithinDistance(q, MINIMUM_DISTANCE)) {
                    innerIgnorePolygons = true;
                    break;
                }
            }
            if (innerIgnorePolygons) {
                outerIgnorePolygons = true;
                break;
            }
        }
        return outerIgnorePolygons;
    }

    private List<List<Long>> constructRings(List<OSMWay> ways) {
        if (ways.isEmpty()) {
            return Collections.emptyList();
        }

        List<List<Long>> closedRings = new ArrayList<>();
        ArrayListMultimap<Long, OSMWay> waysByEndpoint = ArrayListMultimap.create();
        for (OSMWay way : ways) {
            final List<Long> refs = way.getNodeRefs();

            var start = refs.get(0);
            var end = refs.get(refs.size() - 1);

            if (start.equals(end)) {
                ArrayList<Long> ring = new ArrayList<>(refs);
                closedRings.add(ring);
            } else {
                waysByEndpoint.put(start, way);
                waysByEndpoint.put(end, way);
            }
        }

        //check impossible cases
        for (Long endpoint : waysByEndpoint.keySet()) {
            Collection<OSMWay> list = waysByEndpoint.get(endpoint);
            if (list.size() % 2 == 1) {
                return Collections.emptyList();
            }
        }

        List<Long> partialRing = new ArrayList<>();
        if (waysByEndpoint.isEmpty()) {
            return closedRings;
        }

        long firstEndpoint = 0;
        long otherEndpoint = 0;

        OSMWay firstWay = null;

        for (Long endpoint : waysByEndpoint.keySet()) {
            final List<OSMWay> list = waysByEndpoint.get(endpoint);
            firstWay = list.get(0);
            final List<Long> nodeRefs = firstWay.getNodeRefs();
            partialRing.addAll(nodeRefs);
            firstEndpoint = nodeRefs.get(0);
            otherEndpoint = nodeRefs.get(nodeRefs.size() - 1);
            break;
        }
        waysByEndpoint.get(firstEndpoint).remove(firstWay);
        waysByEndpoint.get(otherEndpoint).remove(firstWay);

        if (constructRingsRecursive(waysByEndpoint, partialRing, closedRings, firstEndpoint)) {
            return closedRings;
        } else {
            return Collections.emptyList();
        }

    }

    private boolean constructRingsRecursive(ArrayListMultimap<Long, OSMWay> waysByEndpoint, List<Long> ring, List<List<Long>> closedRings, long endpoint) {
        List<OSMWay> ways = new ArrayList<>(waysByEndpoint.get(endpoint));
        for (OSMWay way : ways) {
            //remove this way from the map
            List<Long> nodeRefs = way.getNodeRefs();
            long firstEndpoint = nodeRefs.get(0);
            long otherEndpoint = nodeRefs.get(nodeRefs.size() - 1);

            waysByEndpoint.remove(firstEndpoint, way);
            waysByEndpoint.remove(otherEndpoint, way);

            ArrayList<Long> newRing = new ArrayList<>(ring.size() + nodeRefs.size());
            long newFirstEndpoint;
            if (firstEndpoint == endpoint) {
                for (int i = nodeRefs.size() - 1; i >= 1; --i) {
                    newRing.add(nodeRefs.get(i));
                }
                newRing.addAll(ring);
                newFirstEndpoint = otherEndpoint;
            } else {
                newRing.addAll(nodeRefs.subList(0, nodeRefs.size() - 1));
                newRing.addAll(ring);
                newFirstEndpoint = firstEndpoint;
            }

            if (newRing.get(newRing.size() - 1).equals(newRing.get(0))) {
                //Closing ring
                closedRings.add(newRing);
                //out of endpoints done parsing
                if (waysByEndpoint.size() == 0) {
                    return true;
                }

                //otherwise start new partial ring
                newRing = new ArrayList<>();
                OSMWay firstWay = null;
                for (Long entry : waysByEndpoint.keySet()) {
                    final List<OSMWay> list = waysByEndpoint.get(entry);
                    firstWay = list.get(0);
                    nodeRefs = firstWay.getNodeRefs();
                    newRing.addAll(nodeRefs);
                    firstEndpoint = nodeRefs.get(0);
                    otherEndpoint = nodeRefs.get(nodeRefs.size() - 1);
                    break;
                }

                waysByEndpoint.remove(firstEndpoint, way);
                waysByEndpoint.remove(otherEndpoint, way);

                if (constructRingsRecursive(waysByEndpoint, newRing, closedRings, firstEndpoint)) {
                    return true;
                }

                waysByEndpoint.remove(firstEndpoint, firstWay);
                waysByEndpoint.remove(otherEndpoint, firstWay);

            } else {
                // Continue with ring
                if (waysByEndpoint.get(newFirstEndpoint) != null) {
                    return constructRingsRecursive(waysByEndpoint, newRing, closedRings, newFirstEndpoint);
                }
            }
            if (firstEndpoint == endpoint) {
                waysByEndpoint.put(otherEndpoint, way);
            } else {
                waysByEndpoint.put(firstEndpoint, way);
            }
        }
        return false;
    }

    @Override
    public void doneSecondPhaseWays() {
        gatherNodesUsedInWaysPhase = false;
    }

    @Override
    public void doneThirdPhaseNodes() {
        processMultipolygonRelations();
    }

    private void processMultipolygonRelations() {
        var counter = 0;
        for (OSMRelation relation : relationsById.values()) {
            if (relation.isTag("type", "multipolygon") && matchesFilter(relation)) {

                var innerWays = new ArrayList<OSMWay>();
                var outerWays = new ArrayList<OSMWay>();

                for (OSMRelationMember member : relation.getMembers()) {
                    final String role = member.getRole();
                    final OSMWay way = waysById.get(member.getRef());

                    if (way != null) {
                        if (role.equals("inner")) {
                            innerWays.add(way);
                        } else if (role.equals("outer")) {
                            outerWays.add(way);
                        } else {
                            logger.warn("Unexpected role " + role + " in multipolygon");
                        }
                    }
                }
                var topographicPlace = map(relation);
                if (addGeometry(innerWays, outerWays, topographicPlace)) {
                    topographicPlaceQueue.add(topographicPlace);
                    counter++;
                }
            }
        }
        logger.info("Total {} multipolygon POIs added.", counter);
    }

    boolean matchesFilter(OSMWithTags entity) {
        if (!entity.hasTag(TAG_NAME)) {
            return false;
        }

        for (Map.Entry<String, String> tag : entity.getTags().entrySet()) {
            if (osmPoiFilters.stream().anyMatch(f -> (tag.getKey().equals(f.getKey()) && tag.getValue().startsWith(f.getValue())))) {
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

        if (descriptors.isEmpty()) {
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
        if (!polygon.isValid()) {
            logger.info("Invalid polygon: " + polygon);
            return null;
        }
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
            centroid = new GeometryFactory().createMultiPointFromCoords(coordinates.toArray(new Coordinate[coordinates.size()])).getCentroid();
        }
        return toCentroid(centroid.getY(), centroid.getX());
    }


    SimplePoint_VersionStructure toCentroid(double latitude, double longitude) {
        return new SimplePoint_VersionStructure().withLocation(
                new LocationStructure().withLatitude(BigDecimal.valueOf(latitude))
                        .withLongitude(BigDecimal.valueOf(longitude)));
    }
}
