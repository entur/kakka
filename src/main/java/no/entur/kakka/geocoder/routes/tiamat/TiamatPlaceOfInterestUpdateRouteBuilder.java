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
import no.entur.kakka.domain.OSMPOIFilter;
import no.entur.kakka.geocoder.BaseRouteBuilder;
import no.entur.kakka.geocoder.netex.TopographicPlaceConverter;
import no.entur.kakka.geocoder.netex.TopographicPlaceReader;
import no.entur.kakka.geocoder.netex.pbf.PbfTopographicPlaceReader;
import no.entur.kakka.geocoder.routes.control.GeoCoderTaskType;
import no.entur.kakka.routes.status.JobEvent;
import no.entur.kakka.services.OSMPOIFilterService;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.LoggingLevel;
import org.rutebanken.netex.model.IanaCountryTldEnumeration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.ws.rs.core.MediaType;
import java.io.File;
import java.util.List;

import static no.entur.kakka.geocoder.GeoCoderConstants.GEOCODER_NEXT_TASK;
import static no.entur.kakka.geocoder.GeoCoderConstants.TIAMAT_EXPORT_START;
import static no.entur.kakka.geocoder.GeoCoderConstants.TIAMAT_PLACES_OF_INTEREST_UPDATE_START;

// TODO specific per source?
@Component
public class TiamatPlaceOfInterestUpdateRouteBuilder extends BaseRouteBuilder {
    /**
     * One time per 24H on MON-FRI
     */
    @Value("${tiamat.poi.update.cron.schedule:0+0+23+?+*+MON-FRI}")
    private String cronSchedule;

    @Value("${tiamat.poi.update.directory:files/tiamat/poi/update}")
    private String localWorkingDirectory;

    @Value("${tiamat.url}")
    private String tiamatUrl;

    @Value("${tiamat.publication.delivery.path:/services/stop_places/netex}")
    private String tiamatPublicationDeliveryPath;

    /**
     * This is the name which the graph file is stored remotely.
     */
    @Value("${otp.graph.file.name:norway-latest.osm.pbf}")
    private String osmFileName;

    @Value("${osm.pbf.blobstore.subdirectory:osm}")
    private String blobStoreSubdirectoryForOsm;

    @Value("${tiamat.poi.update.enabled:true}")
    private boolean routeEnabled;

    @Value("${tiamat.poi.update.eraseTopographicPlaceWithIdPrefixAndTypeValue:OSM;PLACE_OF_INTEREST}")
    private String eraseTopographicPlaceWithIdPrefixAndTypeValue;

    @Autowired
    private TopographicPlaceConverter topographicPlaceConverter;


    @Autowired
    private OSMPOIFilterService osmpoiFilterService;

    @Override
    public void configure() throws Exception {
        super.configure();

        singletonFrom("quartz://kakka/tiamatPlaceOfInterestUpdate?cron=" + cronSchedule + "&trigger.timeZone=Europe/Oslo")
                .autoStartup("{{tiamat.poi.update.autoStartup:false}}")
                .filter(e -> isSingletonRouteActive(e.getFromRouteId()))
                .log(LoggingLevel.INFO, "Quartz triggers Tiamat update of place of interest.")
                .setBody(constant(TIAMAT_PLACES_OF_INTEREST_UPDATE_START))
                .to(ExchangePattern.InOnly,"direct:geoCoderStart")
                .routeId("tiamat-poi-update-quartz");

        from(TIAMAT_PLACES_OF_INTEREST_UPDATE_START.getEndpoint())
                .choice()
                .when(constant(routeEnabled))
                .log(LoggingLevel.INFO, "Start updating POI information in Tiamat")
                .process(e -> JobEvent.systemJobBuilder(e).startGeocoder(GeoCoderTaskType.TIAMAT_POI_UPDATE).build()).to("direct:updateStatus")
                .setHeader(Exchange.FILE_PARENT, constant(localWorkingDirectory))
                .doTry()
                .to("direct:cleanUpLocalDirectory")
                .to("direct:fetchPlaceOfInterest")
                .to("direct:mapPlaceOfInterestToNetex")
                .to("direct:updatePlaceOfInterestInTiamat")
                .to("direct:processTiamatPlaceOfInterestUpdateCompleted")
                .log(LoggingLevel.INFO, "Completed job updating POI information in Tiamat")
                .doFinally()
                .to("direct:cleanUpLocalDirectory")
                .endChoice()
                .otherwise()
                .log(LoggingLevel.WARN, "Tiamat PlaceOfInterest update route has been DISABLED. Will not update POIs or proceed with geocoder route.")
                .end()
                .routeId("tiamat-poi-update");


        from("direct:fetchPlaceOfInterest")
                .log(LoggingLevel.DEBUG, getClass().getName(), "Fetching latest osm poi data ...")
                .setHeader(Constants.FILE_HANDLE, simple(blobStoreSubdirectoryForOsm + "/" + osmFileName))
                .to("direct:getBlob")
                .setHeader(Exchange.FILE_NAME, constant(osmFileName))
                .to("file:" + localWorkingDirectory)
                .routeId("tiamat-fetch-poi-osm");

        from("direct:mapPlaceOfInterestToNetex")
                .log(LoggingLevel.DEBUG, getClass().getName(), "Mapping latest place of interest to Netex ...")
                .process(e -> topographicPlaceConverter.toNetexFile(createTopographicPlaceReader(e), localWorkingDirectory + "/poi-netex.xml"))
                .process(e -> e.getIn().setBody(new File(localWorkingDirectory + "/poi-netex.xml")))
                .routeId("tiamat-map-poi-osm-to-netex");

        from("direct:updatePlaceOfInterestInTiamat")
                .setHeader(Exchange.HTTP_METHOD, constant(org.apache.camel.component.http.HttpMethods.POST))
                .setHeader(Exchange.CONTENT_TYPE, simple(MediaType.APPLICATION_XML))
                .setHeader(Exchange.HTTP_QUERY, simple("eraseTopographicPlaceWithIdPrefixAndType=" + eraseTopographicPlaceWithIdPrefixAndTypeValue))
                .process("authorizationHeaderProcessor")
                .to(tiamatUrl + tiamatPublicationDeliveryPath)
                .routeId("tiamat-poi-update-start");

        from("direct:processTiamatPlaceOfInterestUpdateCompleted")
                .setProperty(GEOCODER_NEXT_TASK, constant(TIAMAT_EXPORT_START))
                .process(e -> JobEvent.systemJobBuilder(e).state(JobEvent.State.OK).build()).to("direct:updateStatus")
                .routeId("tiamat-poi-update-completed");

    }

    private TopographicPlaceReader createTopographicPlaceReader(Exchange e) {
        List<OSMPOIFilter> osmpoiFilterList = osmpoiFilterService.getFilters();

        if (osmpoiFilterList == null) {
            log.warn("No custom configuration defined for poiFilter in database");
        } else {
            if (osmpoiFilterList.isEmpty()) {
                log.warn("No poiFilter values exist in database");
            }
        }

        return new PbfTopographicPlaceReader(osmpoiFilterList, IanaCountryTldEnumeration.NO, new File(localWorkingDirectory + "/" + osmFileName));
    }

}
