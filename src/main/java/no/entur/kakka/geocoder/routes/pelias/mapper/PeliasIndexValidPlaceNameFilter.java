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

package no.entur.kakka.geocoder.routes.pelias.mapper;

import no.entur.kakka.geocoder.GeoCoderConstants;
import no.entur.kakka.geocoder.routes.pelias.elasticsearch.ElasticsearchCommand;
import no.entur.kakka.geocoder.routes.pelias.json.GeoPoint;
import no.entur.kakka.geocoder.routes.pelias.json.PeliasDocument;
import no.entur.kakka.geocoder.services.AdminUnitRepository;
import org.apache.camel.Body;
import org.apache.camel.ExchangeProperty;
import org.geotools.geometry.jts.JTS;
import org.geotools.referencing.GeodeticCalculator;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.opengis.geometry.DirectPosition;
import org.opengis.referencing.operation.TransformException;
import org.rutebanken.netex.model.SimplePoint_VersionStructure;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class PeliasIndexValidPlaceNameFilter {


    private final Logger logger = LoggerFactory.getLogger(getClass());
    private AdminUnitRepository adminUnitRepository;

    /**
     * Remove place name which withen 1KM distance to group of stopplaces
     * <p>
     * Certain commands will be acceptable for insert into Elasticsearch, but will cause Pelias API to fail upon subsequent queries.
     */
    public List<ElasticsearchCommand> removeInvalidCommands(@Body Collection<ElasticsearchCommand> commands,
                                                            @ExchangeProperty(value = GeoCoderConstants.GEOCODER_ADMIN_UNIT_REPO) AdminUnitRepository adminUnitRepository) {

        this.adminUnitRepository = adminUnitRepository;

        return commands.stream().filter(c -> isValid(c)).collect(Collectors.toList());
    }

    boolean isValid(ElasticsearchCommand command) {
        if (command == null || command.getIndex() == null) {
            logger.warn("Removing invalid command");
            return false;
        }
        if (command.getIndex().getIndex() == null || command.getIndex().getType() == null) {
            logger.warn("Removing invalid command with missing index name or type:" + command);
            return false;
        }

        if (!(command.getSource() instanceof PeliasDocument)) {
            logger.warn("Removing invalid command with missing pelias document:" + command);
            return false;
        }

        PeliasDocument doc = (PeliasDocument) command.getSource();

        if (doc.getLayer() == null || doc.getSource() == null || doc.getSourceId() == null) {
            logger.warn("Removing invalid command where pelias document is missing mandatory fields:" + command);
            return false;
        }

        if (doc.getCenterPoint() == null) {
            logger.debug("Removing invalid command where geometry is missing:" + command);
            return false;
        }

        if ((adminUnitRepository.getGroupOfStopPlaces(doc.getDefaultName())) != null) {
            logger.debug("Removing placename which same as groupofstopplace name");
            final SimplePoint_VersionStructure gospCentroid = adminUnitRepository.getGroupOfStopPlaces(doc.getDefaultName()).getCentroid();
            final GeoPoint placeNameCenterPoint = doc.getCenterPoint();
            if (gospCentroid != null) {
                Point placeNamePoint = new GeometryFactory().createPoint(new Coordinate(placeNameCenterPoint.getLon(), placeNameCenterPoint.getLat()));
                Point gospPoint = new GeometryFactory().createPoint(new Coordinate(gospCentroid.getLocation().getLongitude().doubleValue(), gospCentroid.getLocation().getLatitude().doubleValue()));


                final GeodeticCalculator gc = new GeodeticCalculator();

                final DirectPosition placeDirectPosition = JTS.toDirectPosition(new Coordinate(placeNameCenterPoint.getLon(), placeNameCenterPoint.getLat()), gc.getCoordinateReferenceSystem());
                final DirectPosition gospDirectPosition = JTS.toDirectPosition(new Coordinate(gospCentroid.getLocation().getLongitude().doubleValue(), gospCentroid.getLocation().getLatitude().doubleValue()), gc.getCoordinateReferenceSystem());

                try {
                    gc.setStartingPosition(placeDirectPosition);
                    gc.setDestinationPosition(gospDirectPosition);
                } catch (TransformException e) {
                    e.printStackTrace();
                }


                final double orthodromicDistance = gc.getOrthodromicDistance();

                int totalDistanceMeters = (int) orthodromicDistance;


                logger.debug(String.format("placename cordinates: %s", placeNamePoint));
                logger.debug(String.format("gosp cordinates: %s", gospPoint));
                logger.debug(String.format("Distance between placename and gosp %s is %s", doc.getDefaultName(), totalDistanceMeters));

                return totalDistanceMeters >= 1000;

            }

        }

        return true;
    }

}
