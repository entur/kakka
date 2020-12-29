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
import no.entur.kakka.geocoder.BaseRouteBuilder;
import no.entur.kakka.geocoder.GeoCoderConstants;
import no.entur.kakka.geocoder.netex.TopographicPlaceConverter;
import no.entur.kakka.geocoder.netex.geojson.GeoJsonSingleTopographicPlaceReader;
import no.entur.kakka.geocoder.netex.osm.OsmToNetexTransformer;
import no.entur.kakka.geocoder.netex.sosi.SosiTopographicPlaceReader;
import no.entur.kakka.geocoder.routes.control.GeoCoderTaskType;
import no.entur.kakka.geocoder.sosi.SosiElementWrapperFactory;
import no.entur.kakka.routes.file.ZipFileUtils;
import no.entur.kakka.routes.status.JobEvent;
import no.entur.kakka.security.TokenService;
import no.entur.kakka.services.BlobStoreService;
import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.apache.commons.io.FileUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.ws.rs.core.MediaType;
import java.io.File;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class TiamatTariffZonesUpdateRouteBuilder extends BaseRouteBuilder {


    @Value("${tiamat.countries.geojson.blobstore.subdirectory:tariffzones}")
    private String blobStoreSubdirectory;

    @Value("${tiamat.url}")
    private String tiamatUrl;

    @Value("${tiamat.publication.delivery.path:/services/stop_places/netex}")
    private String tiamatPublicationDeliveryPath;

    @Value("${tiamat.tariffzones.update.directory:files/tiamat/tariffZones}")
    private String localWorkingDirectory;


    //TODO TariffZone Converter like @Autowired TopographicPlaceConverter

    @Autowired
    private TokenService tokenService;

    @Autowired
    private BlobStoreService blobStoreService;


    @Autowired
    private OsmToNetexTransformer osmToNetexTransformer;


    @Override
    public void configure() throws Exception {
        super.configure();

        from(GeoCoderConstants.TIAMAT_TARIFF_ZONES_UPDATE_START.getEndpoint())
                .log(LoggingLevel.INFO, "Starting update of tariff zones in Tiamat")
                .setHeader(Exchange.FILE_PARENT, constant(localWorkingDirectory))
                .doTry()
                .to("direct:cleanUpLocalDirectory")
                .to("direct:fetchTariffZones")
                .to("direct:mapTariffZonesToNetex")
                .to("direct:updateTariffZonesInTiamat")
                .log(LoggingLevel.INFO, "Finished updating tariff zones in Tiamat")
                .doFinally()
                .to("direct:cleanUpLocalDirectory")
                .end()

                .routeId("tiamat-tariff-zones-update");

        from("direct:fetchTariffZones")
                .log(LoggingLevel.DEBUG, getClass().getName(), "Fetching tariff zones ...")

                .process(e -> e.getIn().setBody(blobStoreService.listBlobsInFolder(blobStoreSubdirectory, e).getFiles().stream().filter(f -> f.getName().endsWith("osm")).collect(Collectors.toList())))
                .split().body()
                .setHeader(Constants.FILE_HANDLE, simple("${body.name}"))
                .process(e -> e.getIn().setHeader(Exchange.FILE_NAME, Paths.get(e.getIn().getBody(BlobStoreFiles.File.class).getName()).getFileName()))
                .to("direct:getBlob")
                .to("file:" + localWorkingDirectory)
                .routeId("tiamat-fetch-tariff-zones");

        from("direct:mapTariffZonesToNetex")
                .log(LoggingLevel.DEBUG, getClass().getName(), "Mapping Tariff zones to Netex ...")
                .process(e -> osmToNetexTransformer.transform(getTariffZonesFiles(),localWorkingDirectory))
                .routeId("tiamat-osm-tariff-zones-to-netex");

        from("direct:updateTariffZonesInTiamat")
                .setHeader(Exchange.HTTP_METHOD, constant(org.apache.camel.component.http4.HttpMethods.POST))
                .setHeader(Exchange.CONTENT_TYPE, simple(MediaType.APPLICATION_XML))
                .process(e -> e.getIn().setHeader("Authorization", "Bearer " + tokenService.getToken()))
                .to(tiamatUrl + tiamatPublicationDeliveryPath)
                .routeId("tiamat-tariff-zones-update-start");

    }

    private File[] getTariffZonesFiles() {
        return FileUtils.listFiles(new File(localWorkingDirectory), new String[]{"osm"}, false).toArray(File[]::new);
    }

}
