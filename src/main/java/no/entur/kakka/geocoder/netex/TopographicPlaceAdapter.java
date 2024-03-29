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

package no.entur.kakka.geocoder.netex;

import org.locationtech.jts.geom.Geometry;

import java.util.List;
import java.util.Map;

public interface TopographicPlaceAdapter {
    String getId();

    String getIsoCode();

    String getParentId();

    String getName();

    TopographicPlaceAdapter.Type getType();

    Geometry getDefaultGeometry();

    /**
     * Returns map of languages as keys and corresponding name as value.
     */
    Map<String, String> getAlternativeNames();

    /**
     * Returns two letter country code.
     */
    String getCountryRef();

    List<String> getCategories();

    boolean isValid();

    enum Type {COUNTRY, COUNTY, LOCALITY, BOROUGH, PLACE}
}
