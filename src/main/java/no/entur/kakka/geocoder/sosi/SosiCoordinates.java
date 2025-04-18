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

import no.entur.kakka.geocoder.routes.pelias.mapper.coordinates.GeometryTransformer;
import no.entur.kakka.geocoder.routes.pelias.mapper.kartverket.KartverketCoordinatSystemMapper;
import no.vegvesen.nvdb.sosi.document.SosiElement;
import no.vegvesen.nvdb.sosi.document.SosiNumber;
import no.vegvesen.nvdb.sosi.document.SosiSerialNumber;
import no.vegvesen.nvdb.sosi.document.SosiValue;
import org.locationtech.jts.geom.Coordinate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SosiCoordinates {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final Map<Long, List<Coordinate>> coordinatesMap = new HashMap<>();
    private double unit = 0.01;
    private String utmZone = "33";

    public SosiCoordinates(SosiElement head) {
        SosiElement transpar = head.findSubElement(se -> "TRANSPAR".equals(se.getName())).orElse(null);

        if (transpar != null) {
            SosiElement unitElement = transpar.findSubElement(se -> "ENHET".equals(se.getName())).orElse(null);
            if (unitElement != null) {
                unit = unitElement.getValueAs(SosiNumber.class).doubleValue();
            } else {
                logger.warn("Unable to read unit from SOSI file header. Using default value: {}", unit);
            }

            SosiElement coordSysElement = transpar.findSubElement(se -> "KOORDSYS".equals(se.getName())).orElse(null);
            if (coordSysElement != null) {
                utmZone = KartverketCoordinatSystemMapper.toUTMZone(coordSysElement.getValueAs(SosiValue.class).toString());
            } else {
                logger.warn("Unable to read utmZone from SOSI file header. Using default value: {}", utmZone);
            }


        } else {
            logger.warn("Unable to read TRANSPAR from Sosi file header. Relying on default values.");
        }
    }

    public List<Coordinate> getForRef(Long ref) {
        return coordinatesMap.get(ref);
    }

    public void collectCoordinates(SosiElement sosiElement) {
        if (sosiElement.getName().equals("KURVE") || sosiElement.getName().equals("BUEP")) {

            Long id = sosiElement.getValueAs(SosiSerialNumber.class).longValue();

            List<SosiNumber> sosiNumbers = new ArrayList<>();
            sosiElement.subElements().filter(se -> "NØ".equals(se.getName())).forEach(se -> sosiNumbers.addAll(se.getValuesAs(SosiNumber.class)));

            List<Coordinate> coordinates = toLatLonCoordinates(sosiNumbers);

            coordinatesMap.put(id, coordinates);
        }

    }

    public List<Coordinate> toLatLonCoordinates(List<SosiNumber> sosiNumbers) {
        List<Coordinate> coordinates = new ArrayList<>();
        Double y = null;
        for (SosiNumber sosiNumber : sosiNumbers) {
            if (y == null) {
                y = sosiNumber.longValue() * unit;
            } else {
                double x = sosiNumber.longValue() * unit;
                try {
                    Coordinate utmCoord = new Coordinate(x, y);
                    coordinates.add(GeometryTransformer.fromUTM(utmCoord, utmZone));
                } catch (Exception e) {
                    logger.warn("Failed to convert coordinates from utm to wgs84:{}", e.getMessage(), e);
                }
                y = null;
            }
        }
        return coordinates;
    }

}
