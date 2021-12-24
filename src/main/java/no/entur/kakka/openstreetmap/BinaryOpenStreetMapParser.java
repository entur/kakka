/* This program is free software: you can redistribute it and/or
 modify it under the terms of the GNU Lesser General Public License
 as published by the Free Software Foundation, either version 3 of
 the License, or (at your option) any later version.

 This program is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU General Public License for more details.

 You should have received a copy of the GNU General Public License
 along with this program.  If not, see <http://www.gnu.org/licenses/>. */

package no.entur.kakka.openstreetmap;

import crosby.binary.BinaryParser;
import crosby.binary.Osmformat;
import no.entur.kakka.openstreetmap.model.OSMNode;
import no.entur.kakka.openstreetmap.model.OSMNodeRef;
import no.entur.kakka.openstreetmap.model.OSMRelation;
import no.entur.kakka.openstreetmap.model.OSMRelationMember;
import no.entur.kakka.openstreetmap.model.OSMTag;
import no.entur.kakka.openstreetmap.model.OSMWay;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Parser for the OpenStreetMap PBF Format.
 * <p>
 * Copied from OpenTripPlanner - https://github.com/opentripplanner/OpenTripPlanner
 */
public class BinaryOpenStreetMapParser extends BinaryParser {
    private final OpenStreetMapContentHandler handler;
    private final Map<String, String> stringTable = new HashMap<>();
    private boolean parseWays = true;
    private boolean parseRelations = true;
    private boolean parseNodes = true;

    public BinaryOpenStreetMapParser(OpenStreetMapContentHandler handler) {
        this.handler = handler;
    }

    // The strings are already being pulled from a string table in the PBF file,
    // but there appears to be a separate string table per 8k-entry PBF file block.
    // String.intern grinds to a halt on large PBF files (as it did on GTFS import), so 
    // we implement our own. 
    public String internalize(String s) {
        String fromTable = stringTable.get(s);
        if (fromTable == null) {
            stringTable.put(s, s);
            return s;
        }
        return fromTable;
    }

    public void complete() {
        // Jump in circles
    }

    @Override
    protected void parseNodes(List<Osmformat.Node> nodes) {
        if (!parseNodes) {
            return;
        }

        for (Osmformat.Node i : nodes) {
            OSMNode tmp = new OSMNode();
            tmp.setId(i.getId());
            tmp.lat = parseLat(i.getLat());
            tmp.lon = parseLon(i.getLon());

            for (int j = 0; j < i.getKeysCount(); j++) {
                String key = internalize(getStringById(i.getKeys(j)));
                String value = internalize(getStringById(i.getVals(j)));
                OSMTag tag = new OSMTag();
                tag.setK(key);
                tag.setV(value);
                tmp.addTag(tag);
            }

            handler.addNode(tmp);
        }
    }

    @Override
    protected void parseDense(Osmformat.DenseNodes nodes) {
        long lastId = 0, lastLat = 0, lastLon = 0;
        int j = 0; // Index into the keysvals array.

        if (!parseNodes) {
            return;
        }

        for (int i = 0; i < nodes.getIdCount(); i++) {
            OSMNode tmp = new OSMNode();

            long lat = nodes.getLat(i) + lastLat;
            lastLat = lat;
            long lon = nodes.getLon(i) + lastLon;
            lastLon = lon;
            long id = nodes.getId(i) + lastId;
            lastId = id;
            double latf = parseLat(lat), lonf = parseLon(lon);

            tmp.setId(id);
            tmp.lat = latf;
            tmp.lon = lonf;

            // If empty, assume that nothing here has keys or vals.
            if (nodes.getKeysValsCount() > 0) {
                while (nodes.getKeysVals(j) != 0) {
                    int keyid = nodes.getKeysVals(j++);
                    int valid = nodes.getKeysVals(j++);

                    OSMTag tag = new OSMTag();
                    String key = internalize(getStringById(keyid));
                    String value = internalize(getStringById(valid));
                    tag.setK(key);
                    tag.setV(value);
                    tmp.addTag(tag);
                }
                j++; // Skip over the '0' delimiter.
            }

            handler.addNode(tmp);
        }
    }

    @Override
    protected void parseWays(List<Osmformat.Way> ways) {
        if (!parseWays) {
            return;
        }

        for (Osmformat.Way i : ways) {
            OSMWay tmp = new OSMWay();
            tmp.setId(i.getId());

            for (int j = 0; j < i.getKeysCount(); j++) {
                OSMTag tag = new OSMTag();
                String key = internalize(getStringById(i.getKeys(j)));
                String value = internalize(getStringById(i.getVals(j)));
                tag.setK(key);
                tag.setV(value);
                tmp.addTag(tag);
            }

            long lastId = 0;
            for (long j : i.getRefsList()) {
                OSMNodeRef nodeRef = new OSMNodeRef();
                nodeRef.setRef(j + lastId);
                tmp.addNodeRef(nodeRef);

                lastId = j + lastId;
            }

            handler.addWay(tmp);
        }
    }

    @Override
    protected void parseRelations(List<Osmformat.Relation> relations) {
        if (!parseRelations) {
            return;
        }

        for (Osmformat.Relation i : relations) {
            OSMRelation tmp = new OSMRelation();
            tmp.setId(i.getId());

            for (int j = 0; j < i.getKeysCount(); j++) {
                OSMTag tag = new OSMTag();
                String key = internalize(getStringById(i.getKeys(j)));
                String value = internalize(getStringById(i.getVals(j)));
                tag.setK(key);
                tag.setV(value);
                tmp.addTag(tag);
            }

            long lastMid = 0;
            for (int j = 0; j < i.getMemidsCount(); j++) {
                OSMRelationMember relMember = new OSMRelationMember();
                long mid = lastMid + i.getMemids(j);

                relMember.setRef(mid);
                lastMid = mid;

                relMember.setRole(internalize(getStringById(i.getRolesSid(j))));

                switch (i.getTypes(j)) {
                    case NODE:
                        relMember.setType("node");
                        break;
                    case WAY:
                        relMember.setType("way");
                        break;
                    case RELATION:
                        relMember.setType("relation");
                        break;
                    default:
                        assert false;
                        break;
                }

                tmp.addMember(relMember);
            }

            handler.addRelation(tmp);
        }


    }

    @Override
    public void parse(Osmformat.HeaderBlock block) {
        block.getRequiredFeaturesList().stream().filter(s -> !s.equals("OsmSchema-V0.6")).filter(s -> !s.equals("DenseNodes")).forEach(s -> {
            throw new IllegalStateException("File requires unknown feature: " + s);
        });
    }

    /**
     * Should relations be parsed
     */

    public void setParseRelations(boolean parseRelations) {
        this.parseRelations = parseRelations;
    }

    /**
     * Should ways be parsed
     */
    public void setParseWays(boolean parseWays) {
        this.parseWays = parseWays;
    }

    /**
     * Should nodes be parsed
     */
    public void setParseNodes(boolean parseNodes) {
        this.parseNodes = parseNodes;
    }
}
