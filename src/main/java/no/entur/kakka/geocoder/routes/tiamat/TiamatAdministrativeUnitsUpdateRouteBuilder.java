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


import no.entur.kakka.geocoder.BaseRouteBuilder;
import no.entur.kakka.geocoder.GeoCoderConstants;
import no.entur.kakka.geocoder.netex.TopographicPlaceConverter;
import no.entur.kakka.geocoder.netex.sosi.SosiTopographicPlaceReader;
import no.entur.kakka.geocoder.routes.control.GeoCoderTaskType;
import no.entur.kakka.geocoder.sosi.SosiElementWrapperFactory;
import no.entur.kakka.routes.file.ZipFileUtils;
import no.entur.kakka.routes.status.JobEvent;
import no.entur.kakka.services.BlobStoreService;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.LoggingLevel;
import org.apache.commons.io.FileUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.ws.rs.core.MediaType;
import java.io.File;

@Component
public class TiamatAdministrativeUnitsUpdateRouteBuilder extends BaseRouteBuilder {

    @Value("${kartverket.blobstore.subdirectory:kartverket}")
    private String blobStoreSubdirectoryForKartverket;

    @Value("${tiamat.administrative.units.update.cron.schedule:0+0+23+?+*+MON-FRI}")
    private String cronSchedule;

    @Value("${tiamat.url}")
    private String tiamatUrl;

    @Value("${tiamat.publication.delivery.path:/services/stop_places/netex}")
    private String tiamatPublicationDeliveryPath;

    @Value("${tiamat.administrative.units.update.directory:files/tiamat/adminUnits}")
    private String localWorkingDirectory;

    @Autowired
    private TopographicPlaceConverter topographicPlaceConverter;

    @Autowired
    private BlobStoreService blobStore;

    @Autowired
    private SosiElementWrapperFactory wrapperFactory;

    @Override
    public void configure() throws Exception {
        super.configure();

        singletonFrom("quartz://kakka/tiamatAdministrativeUnitsUpdate?cron=" + cronSchedule + "&trigger.timeZone=Europe/Oslo")
                .autoStartup("{{tiamat.administrative.units.update.autoStartup:false}}")
                .filter(e -> shouldQuartzRouteTrigger(e,cronSchedule))
                .log(LoggingLevel.INFO, "Quartz triggers Tiamat update of administrative units.")
                .setBody(constant(GeoCoderConstants.TIAMAT_ADMINISTRATIVE_UNITS_UPDATE_START))
                .to(ExchangePattern.InOnly, "direct:geoCoderStart")
                .routeId("tiamat-admin-units-update-quartz");

        from(GeoCoderConstants.TIAMAT_ADMINISTRATIVE_UNITS_UPDATE_START.getEndpoint())
                .log(LoggingLevel.INFO, "Starting update of administrative units in Tiamat")
                .process(e -> JobEvent.systemJobBuilder(e).startGeocoder(GeoCoderTaskType.TIAMAT_ADMINISTRATIVE_UNITS_UPDATE).build()).to("direct:updateStatus")
                .setHeader(Exchange.FILE_PARENT, constant(localWorkingDirectory))
                .doTry()
                .to("direct:cleanUpLocalDirectory")
                .to("direct:mapAdministrativeUnitsToNetex")
                .to("direct:updateAdministrativeUnitsInTiamat")
                .to("direct:processTiamatAdministrativeUnitsUpdateCompleted")
                .log(LoggingLevel.INFO, "Finished updating administrative units in Tiamat")
                .doFinally()
                .to("direct:cleanUpLocalDirectory")
                .end()

                .routeId("tiamat-admin-units-update");

        from("direct:mapAdministrativeUnitsToNetex")
                .log(LoggingLevel.DEBUG, getClass().getName(), "Mapping latest administrative units to Netex ...")
                .process(e -> {

                    blobStore.listBlobsInFolder(blobStoreSubdirectoryForKartverket + "/administrativeUnits", e).getFiles().stream()
                            .filter(blob -> blob.getName().endsWith(".zip"))
                            .forEach(blob -> ZipFileUtils.unzipFile(blobStore.getBlob(blob.getName(), e), localWorkingDirectory));
                    topographicPlaceConverter.toNetexFile(
                            new SosiTopographicPlaceReader(wrapperFactory, FileUtils.listFiles(new File(localWorkingDirectory), new String[]{"sos"}, true)), localWorkingDirectory + "/admin-units-netex.xml");
                    new File(localWorkingDirectory).delete();
                    e.getIn().setBody(new File(localWorkingDirectory + "/admin-units-netex.xml"));
                })
                .routeId("tiamat-map-admin-units-sosi-to-netex");

        from("direct:updateAdministrativeUnitsInTiamat")
                .setHeader(Exchange.HTTP_METHOD, constant(org.apache.camel.component.http.HttpMethods.POST))
                .setHeader(Exchange.CONTENT_TYPE, simple(MediaType.APPLICATION_XML))
                .process("authorizationHeaderProcessor")
                .to(tiamatUrl + tiamatPublicationDeliveryPath)
                .routeId("tiamat-admin-units-update-start");


        from("direct:processTiamatAdministrativeUnitsUpdateCompleted")
                .setProperty(GeoCoderConstants.GEOCODER_NEXT_TASK, constant(GeoCoderConstants.TIAMAT_EXPORT_START))
                .process(e -> JobEvent.systemJobBuilder(e).state(JobEvent.State.OK).build()).to("direct:updateStatus")
                .routeId("tiamat-admin-units-update-completed");
    }


}
