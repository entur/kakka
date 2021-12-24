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

package no.entur.kakka.geocoder.routes.kartverket;

import no.entur.kakka.Constants;
import no.entur.kakka.geocoder.BaseRouteBuilder;
import no.entur.kakka.geocoder.GeoCoderConstants;
import no.entur.kakka.geocoder.routes.control.GeoCoderTaskType;
import no.entur.kakka.routes.status.JobEvent;
import org.apache.camel.LoggingLevel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class PlaceNamesDownloadRouteBuilder extends BaseRouteBuilder {
    private static final String FORMAT_SOSI = "SOSI";
    /**
     * One time per 24H on MON-FRI
     */
    @Value("${kartverket.place.names.download.cron.schedule:0+0+23+?+*+MON-FRI}")
    private String cronSchedule;
    @Value("${kartverket.blobstore.subdirectory:kartverket}")
    private String blobStoreSubdirectoryForKartverket;
    @Value("${kartverket.place.names.dataSetId:30caed2f-454e-44be-b5cc-26bb5c0110ca}")
    private String placeNamesDataSetId;

    @Override
    public void configure() throws Exception {
        super.configure();

        singletonFrom("quartz://kakka/placeNamesDownload?cron=" + cronSchedule + "&trigger.timeZone=Europe/Oslo")
                .autoStartup("{{kartverket.place.names.download.autoStartup:false}}")
                .filter(e -> isSingletonRouteActive(e.getFromRouteId()))
                .log(LoggingLevel.INFO, "Quartz triggers download of place names.")
                .setBody(constant(GeoCoderConstants.KARTVERKET_PLACE_NAMES_DOWNLOAD))
                .inOnly("direct:geoCoderStart")
                .routeId("place-names-download-quartz");

        from(GeoCoderConstants.KARTVERKET_PLACE_NAMES_DOWNLOAD.getEndpoint())
                .log(LoggingLevel.INFO, "Start downloading place names")
                .process(e -> JobEvent.systemJobBuilder(e).startGeocoder(GeoCoderTaskType.PLACE_NAMES_DOWNLOAD).build()).to("direct:updateStatus")
                .to("direct:transferPlaceNamesFiles")
                .choice()
                .when(simple("${header." + Constants.CONTENT_CHANGED + "}"))
                .log(LoggingLevel.INFO, "Uploaded updated place names from mapping authority. Initiating update of Tiamat")
                .setBody(constant(null))
                .setProperty(GeoCoderConstants.GEOCODER_NEXT_TASK, constant(GeoCoderConstants.PELIAS_UPDATE_START))
                .otherwise()
                .log(LoggingLevel.INFO, "Finished downloading place names from mapping authority with no changes")
                .end()
                .process(e -> JobEvent.systemJobBuilder(e).state(JobEvent.State.OK).build()).to("direct:updateStatus")
                .routeId("place-names-download");


        from("direct:transferPlaceNamesFiles")
                .setHeader(Constants.KARTVERKET_DATASETID, constant(placeNamesDataSetId))
                .setHeader(Constants.FOLDER_NAME, constant(blobStoreSubdirectoryForKartverket + "/placeNames"))
                .setHeader(Constants.KARTVERKET_FORMAT, constant(FORMAT_SOSI))
                .to("direct:uploadUpdatedFiles")
                .routeId("place-names-to-blobstore");

    }


}
