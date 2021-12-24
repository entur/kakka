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

package no.entur.kakka.geocoder.routes.tiamat;

import no.entur.kakka.Constants;
import no.entur.kakka.geocoder.BaseRouteBuilder;
import no.entur.kakka.geocoder.GeoCoderConstants;
import no.entur.kakka.geocoder.routes.control.GeoCoderTaskType;
import no.entur.kakka.routes.status.JobEvent;
import org.apache.camel.LoggingLevel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Route for triggering and publishing Tiamat export to be used for populating geocoder.
 */
@Component
public class TiamatGeoCoderExportRouteBuilder extends BaseRouteBuilder {

    public static String TIAMAT_EXPORT_LATEST_FILE_NAME = "tiamat_export_geocoder_latest.zip";
    @Value("${tiamat.geocoder.export.cron.schedule:0+0+23+?+*+MON-FRI}")
    private String cronSchedule;
    @Value("${tiamat.geocoder.export.cron.schedule.mid.day:0+0+14+?+*+MON-FRI}")
    private String cronScheduleMidDay;
    @Value("${tiamat.geocoder.export.blobstore.subdirectory:tiamat/geocoder}")
    private String blobStoreSubdirectoryForTiamatGeoCoderExport;
    @Value("${tiamat.geocoder.export.query:topographicPlaceExportMode=ALL&versionValidity=CURRENT_FUTURE}")
    private String tiamatExportQuery;

    @Override
    public void configure() throws Exception {
        super.configure();

        singletonFrom("quartz://kakka/tiamatGeoCoderExport?cron=" + cronSchedule + "&trigger.timeZone=Europe/Oslo")
                .autoStartup("{{tiamat.geocoder.export.autoStartup:false}}")
                .filter(e -> isSingletonRouteActive(e.getFromRouteId()))
                .log(LoggingLevel.INFO, "Quartz triggers pelias update.")
                .setBody(constant(GeoCoderConstants.PELIAS_UPDATE_START))
                .inOnly("direct:geoCoderStart")
                .routeId("tiamat-geocoder-export-quartz");

        singletonFrom("quartz://kakka/tiamatGeoCoderExportMidDay?cron=" + cronScheduleMidDay + "&trigger.timeZone=Europe/Oslo")
                .autoStartup("{{tiamat.geocoder.export.mid.day.autoStartup:false}}")
                .filter(e -> isSingletonRouteActive(e.getFromRouteId()))
                .log(LoggingLevel.INFO, "Quartz triggers mid day pelias update.")
                .setBody(constant(GeoCoderConstants.PELIAS_UPDATE_START))
                .inOnly("direct:geoCoderStart")
                .routeId("tiamat-geocoder-export-mid-day-quartz");


        from(GeoCoderConstants.TIAMAT_EXPORT_START.getEndpoint())
                .process(e -> JobEvent.systemJobBuilder(e).startGeocoder(GeoCoderTaskType.TIAMAT_EXPORT).build()).to("direct:updateStatus")
                .setHeader(Constants.JOB_STATUS_ROUTING_DESTINATION, constant("direct:processTiamatGeoCoderExportResults"))
                .setHeader(Constants.QUERY_STRING, constant(tiamatExportQuery))
                .log(LoggingLevel.INFO, "Start Tiamat geocoder export")
                .to("direct:tiamatExport")
                .end()
                .routeId("tiamat-geocoder-export");


        from("direct:processTiamatGeoCoderExportResults")
                .setHeader(Constants.FILE_HANDLE, simple(blobStoreSubdirectoryForTiamatGeoCoderExport + "/" + TIAMAT_EXPORT_LATEST_FILE_NAME))
                .to("direct:tiamatExportMoveFileToBlobStore")
                .routeId("tiamat-geocoder-export-results");

    }
}