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
import org.springframework.stereotype.Service;

import java.util.Collection;

@Service
public class PeliasIndexParentInfoEnricher {

    private GeometryFactory geometryFactory = new GeometryFactory();

    /**
     * Enrich indexing commands with parent info if missing.
     */
    public void addMissingParentInfo(@Body Collection<ElasticsearchCommand> commands,
            @ExchangeProperty(value = GeoCoderConstants.GEOCODER_ADMIN_UNIT_REPO) AdminUnitRepository adminUnitRepository) {
        commands.forEach(c -> addMissingParentInfo(c, adminUnitRepository));
    }

    void addMissingParentInfo(ElasticsearchCommand command, AdminUnitRepository adminUnitRepository) {
        if (!(command.getSource() instanceof PeliasDocument)) {
            return;
        }
        PeliasDocument peliasDocument = (PeliasDocument) command.getSource();

        if (isLocalityMissing(peliasDocument.getParent())) {
            addParentIdsByReverseGeoLookup(adminUnitRepository, peliasDocument);
        }
        addAdminUnitNamesByIds(adminUnitRepository, peliasDocument);
    }

    private void addAdminUnitNamesByIds(AdminUnitRepository adminUnitRepository, PeliasDocument peliasDocument) {
        Parent parent = peliasDocument.getParent();
        if (parent != null) {

            if (parent.getLocalityId() != null && parent.getLocality() == null) {
                String localityName = adminUnitRepository.getAdminUnitName(parent.getLocalityId());
                if (localityName != null) {
                    parent.setLocality(localityName);
                } else {
                    // Locality id on document does not match any known locality, match on geography instead
                    addParentIdsByReverseGeoLookup(adminUnitRepository, peliasDocument);
                    parent.setLocality(adminUnitRepository.getAdminUnitName(parent.getLocalityId()));
                }
            }
            if (parent.getCountyId() != null && parent.getCounty() == null) {
                parent.setCounty(adminUnitRepository.getAdminUnitName(parent.getCountyId()));
            }
            if (parent.getBoroughId() != null && parent.getBorough() == null) {
                parent.setBorough(adminUnitRepository.getAdminUnitName(parent.getBoroughId()));
            }
        }

    }


    private void addParentIdsByReverseGeoLookup(AdminUnitRepository adminUnitRepository, PeliasDocument peliasDocument) {
        Parent parent = peliasDocument.getParent();

        GeoPoint centerPoint = peliasDocument.getCenterPoint();
        if (centerPoint != null) {
            Point point = geometryFactory.createPoint(new Coordinate(centerPoint.getLon(), centerPoint.getLat()));
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

    private boolean isLocalityMissing(Parent parent) {
        return parent == null || parent.getLocalityId() == null;
    }

}
