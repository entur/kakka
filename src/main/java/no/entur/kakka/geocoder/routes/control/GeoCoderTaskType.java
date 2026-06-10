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

package no.entur.kakka.geocoder.routes.control;

import no.entur.kakka.geocoder.GeoCoderConstants;

public enum GeoCoderTaskType {
    ADMINISTRATIVE_UNITS_DOWNLOAD(GeoCoderConstants.KARTVERKET_ADMINISTRATIVE_UNITS_DOWNLOAD),
    TIAMAT_ADMINISTRATIVE_UNITS_UPDATE(GeoCoderConstants.TIAMAT_ADMINISTRATIVE_UNITS_UPDATE_START),
    TIAMAT_NEIGHBOURING_COUNTRIES_UPDATE(GeoCoderConstants.TIAMAT_NEIGHBOURING_COUNTRIES_UPDATE_START);

    private final GeoCoderTask geoCoderTask;

    GeoCoderTaskType(GeoCoderTask geoCoderTask) {
        this.geoCoderTask = geoCoderTask;
    }

    public GeoCoderTask getGeoCoderTask() {
        return geoCoderTask;
    }
}
