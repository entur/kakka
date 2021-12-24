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

import no.entur.kakka.geocoder.netex.TariffZoneConverter;
import no.entur.kakka.security.AuthorizationHeaderProcessor;
import no.entur.kakka.services.BlobStoreService;
import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.ws.rs.core.MediaType;
import java.io.File;
import java.nio.file.Paths;
import java.util.stream.Collectors;


@Component
public class TiamatTariffZonesUpdateRouteBuilder extends BaseRouteBuilder {

    public static final Logger logger= LoggerFactory.getLogger(TiamatTariffZonesUpdateRouteBuilder.class);

    @Value("${tiamat.tariffzones.blobstore.xml.directory:tariffzones/netex}")
    private String blobStoreXmldirectory;

    @Value("${tiamat.tariffzones.blobstore.osm.directory:tariffzones/osm}")
    private String blobStoreOsmdirectory;

    @Value("${tiamat.url}")
    private String tiamatUrl;

    @Value("${tiamat.publication.delivery.path:/services/stop_places/netex}")
    private String tiamatPublicationDeliveryPath;

    @Value("${tiamat.tariffzones.update.directory:files/tiamat/tariffZones}")
    private String localWorkingDirectory;


    private final BlobStoreService blobStoreService;

    private final TariffZoneConverter tariffZoneConverter;



    @Autowired
    public TiamatTariffZonesUpdateRouteBuilder(BlobStoreService blobStoreService,
                                               TariffZoneConverter tariffZoneConverter
                                               ) {
        this.blobStoreService = blobStoreService;
        this.tariffZoneConverter = tariffZoneConverter;
    }

    @Override
    public void configure() throws Exception {
        super.configure();

        onException(KakkaException.class)
                .log(LoggingLevel.ERROR, "Failed while updating  TariffZone file.")
                .handled(true);

        from("entur-google-pubsub:ProcessTariffZoneFileQueue").streamCaching()
                .log(LoggingLevel.INFO, "Starting update of tariff zones in Tiamat: ${header." + Constants.FILE_HANDLE + "}")
                .setHeader(Exchange.FILE_PARENT, constant(localWorkingDirectory))
                .doTry()
                .to("direct:cleanUpLocalDirectory")
                .to("direct:fetchTariffZones")
                .choice()
                .when(header(Constants.FILE_NAME).endsWith("xml"))
                .log(LoggingLevel.INFO,"Updating tariff_zone_netex_file: ${header." + Constants.FILE_NAME + "}")
                .to("direct:updateTariffZonesInTiamat")
                .log(LoggingLevel.INFO, "Finished updating tariff zones in Tiamat")
                .when(header(Constants.FILE_NAME).endsWith("osm"))
                .log(LoggingLevel.INFO,"Converting tariff_zone_osm_file: ${header." + Constants.FILE_NAME + "}")
                .to("direct:mapTariffZonesToNetex")
                .to("direct:updateTariffZonesInTiamat")
                .log(LoggingLevel.INFO, "Finished updating tariff zones in Tiamat")
                .otherwise()
                .log(LoggingLevel.INFO,"Invalid Tariff zone file")
                .endDoTry()
                .doFinally()
                .to("direct:cleanUpLocalDirectory")
                .end()

                .routeId("tiamat-tariff-zones-update");

        from("direct:fetchTariffZones")
                .log(LoggingLevel.DEBUG, getClass().getName(), "Fetching tariff zones ...")

                .process(e -> {
                    String dir = getBlobDir(e.getIn().getHeader(Constants.FILE_NAME, String.class));
                    e.getIn().setBody(blobStoreService.listBlobsInFolder(dir, e).getFiles().stream().filter(f -> f.getName().equals(e.getIn().getHeader(Constants.FILE_HANDLE))).collect(Collectors.toList()));
                })
                .split().body()
                .setHeader(Constants.FILE_HANDLE, simple("${body.name}"))
                .process(e -> e.getIn().setHeader(Exchange.FILE_NAME, Paths.get(e.getIn().getBody(BlobStoreFiles.File.class).getName()).getFileName()))
                .to("direct:getBlob")
                .to("file:" + localWorkingDirectory)
                .routeId("tiamat-fetch-tariff-zones");

        from("direct:mapTariffZonesToNetex")
                .log(LoggingLevel.DEBUG, getClass().getName(), "Mapping Tariff zones to Netex ...")
                .process(e -> tariffZoneConverter.toNetexFile(e,localWorkingDirectory))
                .routeId("tiamat-osm-tariff-zones-to-netex");


        from("direct:updateTariffZonesInTiamat")
                .log(LoggingLevel.INFO,"updating tiamat via import endpoint {}",localWorkingDirectory + "/" + header(Constants.FILE_NAME))
                .process(e -> {
                    final String pathname = localWorkingDirectory + "/" + e.getIn().getHeader(Constants.FILE_NAME);
                    logger.info("local file path is: {}", pathname);
                    e.getIn().setBody(new File(pathname));
                })
                .setHeader(Exchange.HTTP_METHOD, constant(org.apache.camel.component.http.HttpMethods.POST))
                .setHeader(Exchange.CONTENT_TYPE, simple(MediaType.APPLICATION_XML))
                .process("authorizationHeaderProcessor")
                .to(tiamatUrl + tiamatPublicationDeliveryPath)
                .routeId("tiamat-tariff-zones-update-start");

    }

    private String getBlobDir(String fileName) {
        if (fileName.endsWith("osm")) {
            return blobStoreOsmdirectory;
        } else if (fileName.endsWith("xml")) {
            return blobStoreXmldirectory;
        } else {
            throw new KakkaException();
        }
    }

}
