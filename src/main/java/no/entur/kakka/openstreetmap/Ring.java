package no.entur.kakka.openstreetmap;

import no.entur.kakka.openstreetmap.model.OSMNode;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Polygon;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class Ring {

    private Polygon polygon;

    public Ring(List<Long> osmNodes, Map<Long, OSMNode> nodes) {
        ArrayList<Coordinate> coordinates = new ArrayList<>();
        List<OSMNode> nodeList = new ArrayList<>(osmNodes.size());
        for (long nodeId : osmNodes) {
            var node = nodes.get(nodeId);
            if (nodeList.contains(node)) {
                continue;
            }
            var coordinate = new Coordinate(node.lon, node.lat);
            coordinates.add(coordinate);
            nodeList.add(node);
        }
        try {
            polygon = new GeometryFactory().createPolygon(coordinates.toArray(new Coordinate[coordinates.size()]));
        } catch (IllegalArgumentException illegalArgumentException) {
            // unable to create polygon
        }


    }

    public Polygon getPolygon() {
        return polygon;
    }

}
