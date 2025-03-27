/* 
 Copyright 2008 Brian Ferris
 This program is free software: you can redistribute it and/or
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


import no.entur.kakka.openstreetmap.model.OSMNode;
import no.entur.kakka.openstreetmap.model.OSMRelation;
import no.entur.kakka.openstreetmap.model.OSMWay;

/**
 * An interface to process/store parsed OpenStreetMap data.
 * <p>
 * Copied from OpenTripPlanner - <a href="https://github.com/opentripplanner/OpenTripPlanner">...</a>
 */

public interface OpenStreetMapContentHandler {

    /**
     * Stores a node.
     */
    void addNode(OSMNode node);

    /**
     * Stores a way.
     */
    void addWay(OSMWay way);


    void addRelation(OSMRelation relation);

    /**
     * Called after the second phase, when all ways are loaded.
     */
    void doneSecondPhaseWays();

    /**
     * Called after the third and final phase, when all nodes are loaded.
     */
    void doneThirdPhaseNodes();
}
