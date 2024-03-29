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

package no.entur.kakka.geocoder.services;


import no.entur.kakka.geocoder.netex.TopographicPlaceAdapter;
import org.locationtech.jts.geom.Point;
import org.rutebanken.netex.model.GroupOfStopPlaces;

public interface AdminUnitRepository {

    String getAdminUnitName(String id);

    TopographicPlaceAdapter getLocality(String id);

    TopographicPlaceAdapter getLocality(Point point);

    TopographicPlaceAdapter getCountry(Point point);

    GroupOfStopPlaces getGroupOfStopPlaces(String name);
}
