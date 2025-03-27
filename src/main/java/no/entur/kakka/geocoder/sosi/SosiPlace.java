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

package no.entur.kakka.geocoder.sosi;

import no.entur.kakka.geocoder.netex.TopographicPlaceAdapter;
import no.vegvesen.nvdb.sosi.document.SosiElement;
import no.vegvesen.nvdb.sosi.document.SosiNumber;
import no.vegvesen.nvdb.sosi.document.SosiValue;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SosiPlace extends SosiElementWrapper {
    public static final String OBJECT_TYPE = "Sted";

    public SosiPlace(SosiElement sosiElement, SosiCoordinates coordinates) {
        super(sosiElement, coordinates);
    }

    @Override
    public String getId() {
        return getProperty("IDENT", "LOKALID");
    }

    @Override
    public TopographicPlaceAdapter.Type getType() {
        return TopographicPlaceAdapter.Type.PLACE;
    }


    @Override
    protected String getNamePropertyName() {
        // Not applicable
        return null;
    }

    @Override
    protected Map<String, String> getNames() {
        if (names == null) {
            names = new HashMap<>();
            String name = getProperty("STEDSNAVN", "SKRIVEMÅTE", "LANGNAVN");
            if (name == null) {
                name = getCompleteName();
            }
            String lang = getProperty("STEDSNAVN", "SPRÅK");
            if (lang == null) {
                lang = "nor";
            }
            names.put(lang, name);
        }
        return names;
    }

    private String getCompleteName() {
        SosiElement subElement = sosiElement;
        String propName = "KOMPLETTSKRIVEMÅTE";
        subElement = subElement.findSubElementRecursively(se -> propName.equals(se.getName())).orElse(null);

        if (subElement != null) {
            return subElement.getValueAs(SosiValue.class).getString();
        }
        return null;
    }

    @Override
    public boolean isValid() {
        if (SosiSpellingStatusCode.isActive(getProperty("SKRIVEMÅTE", "SKRIVEMÅTESTATUS"))) {
            return false;
        }

        return super.isValid();
    }

    @Override
    public Geometry getDefaultGeometry() {
        if (geometry != null) {
            return geometry;
        }
        List<SosiNumber> sosiNumbers = new ArrayList<>();
        sosiElement.subElements().filter(se -> "NØ".equals(se.getName())).forEach(se -> sosiNumbers.addAll(se.getValuesAs(SosiNumber.class)));

        List<Coordinate> coordinateList = coordinates.toLatLonCoordinates(sosiNumbers);

        if (coordinateList.isEmpty()) {
            return null;
        }

        geometry = new GeometryFactory().createPoint(coordinateList.getFirst());
        return geometry;
    }

    @Override
    public List<String> getCategories() {
        String type = getProperty("NAVNEOBJEKTTYPE");
        List<String> categories = new ArrayList<>();
        if (type != null) {
            categories.add(type);
        }
        return categories;
    }
}
