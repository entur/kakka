package no.entur.kakka.openstreetmap.model;

/**
 * Copied from OpenTripPlanner - https://github.com/opentripplanner/OpenTripPlanner
 */

import java.util.ArrayList;
import java.util.List;

public class OSMRelation extends OSMWithTags {

    private final List<OSMRelationMember> members = new ArrayList<>();

    public void addMember(OSMRelationMember member) {
        members.add(member);
    }

    public List<OSMRelationMember> getMembers() {
        return members;
    }

    public String toString() {
        return "osm relation " + id;
    }
}
