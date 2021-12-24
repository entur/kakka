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
import no.entur.kakka.geocoder.routes.control.GeoCoderTaskType;
import no.entur.kakka.routes.status.JobEvent;
import org.apache.camel.ExchangePattern;
import org.apache.camel.LoggingLevel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import static no.entur.kakka.geocoder.GeoCoderConstants.GEOCODER_NEXT_TASK;
import static no.entur.kakka.geocoder.GeoCoderConstants.KARTVERKET_ADDRESS_DOWNLOAD;
import static no.entur.kakka.geocoder.GeoCoderConstants.PELIAS_UPDATE_START;

@Component
public class AddressDownloadRouteBuilder extends BaseRouteBuilder {

    private static final String FORMAT_CSV = "CSV";
    /**
     * One time per 24H on MON-FRI
     */
    @Value("${kartverket.addresses.download.cron.schedule:0+0+23+?+*+MON-FRI}")
    private String cronSchedule;
    @Value("${kartverket.blobstore.subdirectory:kartverket}")
    private String blobStoreSubdirectoryForKartverket;
    @Value("${kartverket.addresses.dataSetId:f7df7a18-b30f-4745-bd64-d0863812350c}")
    private String addressesDataSetId;

    @Override
    public void configure() throws Exception {
        super.configure();

        singletonFrom("quartz://kakka/addressDownload?cron=" + cronSchedule + "&trigger.timeZone=Europe/Oslo")
                .autoStartup("{{kartverket.address.download.autoStartup:false}}")
                .filter(e -> isSingletonRouteActive(e.getFromRouteId()))
                .log(LoggingLevel.INFO, "Quartz triggers address download.")
                .setBody(constant(KARTVERKET_ADDRESS_DOWNLOAD))
                .to(ExchangePattern.InOnly,"direct:geoCoderStart")
                .routeId("address-download-quartz");

        from(KARTVERKET_ADDRESS_DOWNLOAD.getEndpoint())
                .log(LoggingLevel.INFO, "Start downloading address information from mapping authority")
                .process(e -> JobEvent.systemJobBuilder(e).startGeocoder(GeoCoderTaskType.ADDRESS_DOWNLOAD).build()).to("direct:updateStatus")
                .setHeader(Constants.KARTVERKET_DATASETID, constant(addressesDataSetId))
                .setHeader(Constants.FOLDER_NAME, constant(blobStoreSubdirectoryForKartverket + "/addresses"))
                .setHeader(Constants.KARTVERKET_FORMAT, constant(FORMAT_CSV))
                .to("direct:uploadUpdatedFiles")
                .choice()
                .when(simple("${header." + Constants.CONTENT_CHANGED + "}"))
                .log(LoggingLevel.INFO, "Uploaded updated address information from mapping authority. Initiating update of Pelias")
                .setBody(constant(null))
                .setProperty(GEOCODER_NEXT_TASK, constant(PELIAS_UPDATE_START))
                .otherwise()
                .log(LoggingLevel.INFO, "Finished downloading address information from mapping authority with no changes")
                .end()
                .process(e -> JobEvent.systemJobBuilder(e).state(JobEvent.State.OK).build()).to("direct:updateStatus")
                .routeId("address-download");
    }


}
