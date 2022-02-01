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
import no.entur.kakka.domain.BlobStoreFiles;
import no.entur.kakka.exceptions.KakkaException;
import no.entur.kakka.geocoder.BaseRouteBuilder;
import no.entur.kakka.routes.file.ZipFileUtils;
import no.entur.kakka.routes.status.JobEvent;
import no.entur.kakka.services.BlobStoreService;
import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.apache.camel.component.http.HttpMethods;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.ws.rs.core.MediaType;
import java.io.File;
import java.io.InputStream;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import static no.entur.kakka.Constants.FILE_NAME;


@Component
public class TiamatTariffZonesUpdateRouteBuilder extends BaseRouteBuilder {

    public static final Logger logger = LoggerFactory.getLogger(TiamatTariffZonesUpdateRouteBuilder.class);
    private final BlobStoreService blobStoreService;
    @Value("${tiamat.tariffzones.blobstore.netex.directory:tariffzones}")
    private String blobStoreNetexDirectory;
    @Value("${tiamat.url}")
    private String tiamatUrl;
    @Value("${tiamat.publication.delivery.path:/services/stop_places/netex}")
    private String tiamatPublicationDeliveryPath;
    @Value("${tiamat.tariffzones.update.directory:files/tiamat/tariffZones/}")
    private String localWorkingDirectory;


    @Autowired
    public TiamatTariffZonesUpdateRouteBuilder(BlobStoreService blobStoreService) {
        this.blobStoreService = blobStoreService;
    }

    @Override
    public void configure() throws Exception {
        super.configure();

        onException(KakkaException.class)
                .log(LoggingLevel.ERROR, "Failed while updating  TariffZone file.")
                .handled(true);

        from("entur-google-pubsub:TiamatTariffZoneImportQueue").streamCaching()
                .log(LoggingLevel.INFO, correlation() + "Starting update of tariff zones in Tiamat: ${header." + Constants.FILE_HANDLE + "}")
                .process(e -> JobEvent.providerJobBuilder(e).tariffZoneAction(JobEvent.TaiffZoneAction.IMPORT).state(JobEvent.State.PENDING).build())
                .to("direct:updateStatus")
                .setHeader(Exchange.FILE_PARENT, constant(localWorkingDirectory))
                .doTry()
                .to("direct:cleanUpLocalDirectory")
                .to("direct:fetchTariffZones")
                .choice()
                .when(body().isNull())
                .log(LoggingLevel.WARN, correlation() + "Import failed because blob could not be found")
                .process(e -> JobEvent.providerJobBuilder(e).tariffZoneAction(JobEvent.TaiffZoneAction.CLEAN.IMPORT).state(JobEvent.State.FAILED).build())
                .otherwise()
                .log(LoggingLevel.INFO, "Updating tariff_zone_netex_file: ${header." + Constants.FILE_HANDLE + "}")
                .to("direct:updateTariffZonesInTiamat")
                .log(LoggingLevel.INFO, "Finished updating tariff zones in Tiamat")
                .process(e -> JobEvent.providerJobBuilder(e).tariffZoneAction(JobEvent.TaiffZoneAction.EXPORT).state(JobEvent.State.OK).build())
                .endDoTry()
                .doFinally()
                .to("direct:cleanUpLocalDirectory")
                .to("direct:updateStatus")
                .end()

                .routeId("tiamat-tariff-zones-update");

        from("direct:fetchTariffZones")
                .log(LoggingLevel.DEBUG, getClass().getName(), "Fetching tariff zones ...")

                .process(e -> e.getIn().setBody(blobStoreService.listBlobsInFolder(blobStoreNetexDirectory, e).getFiles().stream()
                        .filter(f -> f.getName().equals(e.getIn().getHeader(Constants.FILE_HANDLE)))
                        .collect(Collectors.toList())))
                .split().body()
                .setHeader(Constants.FILE_HANDLE, simple("${body.name}"))
                .process(e -> e.getIn().setHeader(Exchange.FILE_NAME, Paths.get(e.getIn().getBody(BlobStoreFiles.File.class).getName()).getFileName()))
                .log(LoggingLevel.INFO, getClass().getName(), "to route getBlob in fetchTariffZone")
                .to("direct:getBlob")
                .process(e -> ZipFileUtils.unzipFile(e.getIn().getBody(InputStream.class), localWorkingDirectory))
                .routeId("tiamat-fetch-tariff-zones");


        from("direct:updateTariffZonesInTiamat")
                .log(LoggingLevel.INFO, correlation() + "updating tiamat via import endpoint {}", localWorkingDirectory + header(FILE_NAME))
                .process(e -> {
                    final String pathname = localWorkingDirectory;
                    final File file = new File(pathname);
                    final Optional<File> optionalFile = Arrays.stream(Objects.requireNonNull(file.listFiles())).findFirst();
                    optionalFile.ifPresent(f -> e.getIn().setBody(f));
                })
                .setHeader(Exchange.HTTP_METHOD, constant(HttpMethods.POST))
                .setHeader(Exchange.CONTENT_TYPE, simple(MediaType.APPLICATION_XML))
                .process("authorizationHeaderProcessor")
                .to(tiamatUrl + tiamatPublicationDeliveryPath)
                .routeId("tiamat-tariff-zones-update-start");
    }
}
