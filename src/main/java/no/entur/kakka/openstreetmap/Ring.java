package no.entur.kakka.openstreetmap;

import no.entur.kakka.openstreetmap.model.OSMNode;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Polygon;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class Ring {

    public static final Logger logger= LoggerFactory.getLogger(Ring.class);

    private final List<Long> nodesIds;
    private final Map<Long, OSMNode> nodes;

    public Ring(List<Long> nodesIds, Map<Long, OSMNode> nodes) {
        this.nodesIds = nodesIds;
        this.nodes = nodes;

    }

    public Polygon getPolygon() {
        ArrayList<Coordinate> coordinates = new ArrayList<>();
        for (long nodeId : nodesIds) {
            var node = nodes.get(nodeId);
            var coordinate = new Coordinate(node.lon, node.lat);
            coordinates.add(coordinate);
        }
        try {
            return new GeometryFactory().createPolygon(coordinates.toArray(new Coordinate[coordinates.size()]));
        } catch (IllegalArgumentException illegalArgumentException) {
            logger.debug("Unable to create polygon: " + illegalArgumentException.getMessage());
            return null;
        }
    }

}
