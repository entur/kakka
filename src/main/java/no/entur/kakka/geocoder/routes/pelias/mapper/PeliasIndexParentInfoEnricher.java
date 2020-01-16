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
import no.entur.kakka.geocoder.netex.TopographicPlaceAdapter;
import no.entur.kakka.geocoder.routes.pelias.elasticsearch.ElasticsearchCommand;
import no.entur.kakka.geocoder.routes.pelias.json.GeoPoint;
import no.entur.kakka.geocoder.routes.pelias.json.Parent;
import no.entur.kakka.geocoder.routes.pelias.json.PeliasDocument;
import no.entur.kakka.geocoder.services.AdminUnitRepository;
import org.apache.camel.Body;
import org.apache.camel.ExchangeProperty;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;

@Service
public class PeliasIndexParentInfoEnricher {

    private static final Logger logger= LoggerFactory.getLogger(PeliasIndexParentInfoEnricher.class);

    private GeometryFactory geometryFactory = new GeometryFactory();

    /**
     * Enrich indexing commands with parent info if missing.
     */
    public void addMissingParentInfo(@Body Collection<ElasticsearchCommand> commands,
            @ExchangeProperty(value = GeoCoderConstants.GEOCODER_ADMIN_UNIT_REPO) AdminUnitRepository adminUnitRepository) {
        logger.debug("Start updating missing parent info for {} commands",commands.size());
        var ref = new Object() {
            long commandIdx = 1;
        };
        commands.forEach(c -> {
            addMissingParentInfo(c, adminUnitRepository);
            logger.debug("Updated {} / {} command", ref.commandIdx,commands.size());
            ref.commandIdx++;
        });
    }

    void addMissingParentInfo(ElasticsearchCommand command, AdminUnitRepository adminUnitRepository) {

        if (!(command.getSource() instanceof PeliasDocument)) {
            return;
        }
        PeliasDocument peliasDocument = (PeliasDocument) command.getSource();
        if (isLocalityMissing(peliasDocument.getParent())) {
            logger.debug("Locality is missing doing reverseGeoLookup for :" + peliasDocument.getDefaultName());
            addParentIdsByReverseGeoLookup(adminUnitRepository, peliasDocument);
        }
        addAdminUnitNamesByIds(adminUnitRepository, peliasDocument);

    }

    private void addAdminUnitNamesByIds(AdminUnitRepository adminUnitRepository, PeliasDocument peliasDocument) {
        Parent parent = peliasDocument.getParent();
        if (parent != null) {

            logger.debug("Update parentInfo by Ids");

            if (parent.getLocalityId() != null && parent.getLocality() == null) {
                logger.debug("1. Locality is missing get locality name by id: " + parent.getLocalityId());
                String localityName = adminUnitRepository.getAdminUnitName(parent.getLocalityId());
                if (localityName != null) {
                    parent.setLocality(localityName);
                } else {
                    // Locality id on document does not match any known locality, match on geography instead
                    logger.debug("2. Locality is still missing ,doing Reverse lookup again:  " + parent.getLocalityId());
                    addParentIdsByReverseGeoLookup(adminUnitRepository, peliasDocument);
                    logger.debug("3. Once again setLocalty by Id : " + parent.getLocalityId());
                    parent.setLocality(adminUnitRepository.getAdminUnitName(parent.getLocalityId()));
                }
            }
            if (parent.getCountyId() != null && parent.getCounty() == null) {
                logger.debug("County is missing get county name by id: " + parent.getLocalityId());
                parent.setCounty(adminUnitRepository.getAdminUnitName(parent.getCountyId()));
            }
        }

    }


    private void addParentIdsByReverseGeoLookup(AdminUnitRepository adminUnitRepository, PeliasDocument peliasDocument) {
        Parent parent = peliasDocument.getParent();

        GeoPoint centerPoint = peliasDocument.getCenterPoint();
        if (centerPoint != null) {
            Point point = geometryFactory.createPoint(new Coordinate(centerPoint.getLon(), centerPoint.getLat()));

            TopographicPlaceAdapter country = adminUnitRepository.getCountry(point);
            if (country != null) {
                if (parent == null) {
                    parent = new Parent();
                    peliasDocument.setParent(parent);
                }
                parent.setCountryId(country.getCountryRef());
            }
            if (country == null) {
                TopographicPlaceAdapter locality = adminUnitRepository.getLocality(point);
                if (locality != null) {
                    if (parent == null) {
                        parent = new Parent();
                        peliasDocument.setParent(parent);
                    }
                    parent.setLocalityId(locality.getId());
                    parent.setCountyId(locality.getParentId());
                    parent.setCountryId(locality.getCountryRef());
                }
            }
        }
    }
    private boolean isLocalityMissing(Parent parent) {
        return parent == null || parent.getLocalityId() == null;
    }

}
