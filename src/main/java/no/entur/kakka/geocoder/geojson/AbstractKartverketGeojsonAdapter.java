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

package no.entur.kakka.geocoder.geojson;


import no.entur.kakka.geocoder.netex.TopographicPlaceAdapter;
import org.apache.commons.lang3.StringUtils;
import org.geotools.api.feature.simple.SimpleFeature;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public abstract class AbstractKartverketGeojsonAdapter extends AbstractGeojsonAdapter implements TopographicPlaceAdapter {

    public AbstractKartverketGeojsonAdapter(SimpleFeature feature) {
        super(feature);
    }

    public String getIsoCode() {
        return null;
    }

    public String getParentId() {
        return null;
    }

    public String getName() {
        return getProperty("navn");
    }

    protected String pad(long val, int length) {
        return StringUtils.leftPad("" + val, length, "0");
    }

    @Override
    public Map<String, String> getAlternativeNames() {
        return new HashMap<>();
    }

    @Override
    public String getCountryRef() {
        return "NOR";
    }

    @Override
    public List<String> getCategories() {
        return Collections.emptyList();
    }

    @Override
    public boolean isValid() {
        return true;
    }
}
