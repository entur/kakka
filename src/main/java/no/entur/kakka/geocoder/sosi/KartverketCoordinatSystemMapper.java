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


import java.util.HashMap;
import java.util.Map;

/**
 * KOORDINATSYSTEMKODE;       Sosikoden for koordinatsystemet. Verdiene er:
 * 21   EUREF89 UTM Sone 31
 * 22   EUREF89 UTM Sone 32
 * 23   EUREF89 UTM Sone 33
 * 24   EUREF89 UTM Sone 34
 * 25   EUREF89 UTM Sone 35
 * 26   EUREF89 UTM Sone 36
 */
public class KartverketCoordinatSystemMapper {

    private static final Map<String, String> COORDSYS_MAPPING;

    static {
        COORDSYS_MAPPING = new HashMap<>();
        COORDSYS_MAPPING.put("21", "31");
        COORDSYS_MAPPING.put("22", "32");
        COORDSYS_MAPPING.put("23", "33");
        COORDSYS_MAPPING.put("24", "34");
        COORDSYS_MAPPING.put("25", "35");
        COORDSYS_MAPPING.put("26", "36");

        // EPSG to utm mapping since new dataset from kartverket uses epsg codes
        // https://register.geonorge.no/epsg-koder?register=SOSI+kodelister&text=
        COORDSYS_MAPPING.put("25831", "31");
        COORDSYS_MAPPING.put("25832", "32");
        COORDSYS_MAPPING.put("25833", "33");
        COORDSYS_MAPPING.put("25834", "34");
        COORDSYS_MAPPING.put("25835", "35");
        COORDSYS_MAPPING.put("25836", "36");
    }

    public static String toUTMZone(String kartverketCoordinateSystemCode) {
        return COORDSYS_MAPPING.get(kartverketCoordinateSystemCode);
    }
}
