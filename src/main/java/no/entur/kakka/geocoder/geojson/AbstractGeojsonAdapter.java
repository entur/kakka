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

import org.locationtech.jts.geom.Geometry;
import org.geotools.api.feature.simple.SimpleFeature;
import org.geotools.api.feature.Property;

public abstract class AbstractGeojsonAdapter {

    protected SimpleFeature feature;

    public AbstractGeojsonAdapter(SimpleFeature feature) {
        this.feature = feature;
    }

    public Geometry getDefaultGeometry() {
        if (feature.getDefaultGeometryProperty() != null) {
            Object geometry = feature.getDefaultGeometryProperty().getValue();
            if (geometry instanceof Geometry defaultGeometry) {
                return defaultGeometry;
            }
        }
        return null;
    }


    public <T> T getProperty(String propertyName) {
        Property property = feature.getProperty(propertyName);
        if (property == null) {
            return null;
        }
        return (T) property.getValue();
    }
}
