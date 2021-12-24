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
import no.entur.kakka.geocoder.geojson.GeojsonFeatureWrapperFactory;
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
import java.util.stream.Collectors;

@Component
public class TiamatCountryUpdateRouteBuilder extends BaseRouteBuilder {

    @Value("${tiamat.countries.geojson.blobstore.subdirectory:geojson/countries}")
    private String blobStoreSubdirectory;

    @Value("${tiamat.url}")
    private String tiamatUrl;

    @Value("${tiamat.publication.delivery.path:/services/stop_places/netex}")
    private String tiamatPublicationDeliveryPath;

    @Value("${tiamat.neighbouring.countries.update.directory:files/tiamat/countries}")
    private String localWorkingDirectory;

    @Autowired
    private TopographicPlaceConverter topographicPlaceConverter;

    @Autowired
    private BlobStoreService blobStoreService;

    @Autowired
    private GeojsonFeatureWrapperFactory wrapperFactory;

    @Override
    public void configure() throws Exception {
        super.configure();

        from(GeoCoderConstants.TIAMAT_NEIGHBOURING_COUNTRIES_UPDATE_START.getEndpoint())
                .log(LoggingLevel.INFO, "Starting update of neighbouring countries in Tiamat")

                .setHeader(Exchange.FILE_PARENT, constant(localWorkingDirectory))
                .doTry()
                .to("direct:cleanUpLocalDirectory")
                .to("direct:fetchNeighbouringCountries")
                .to("direct:mapNeighbouringCountriesToNetex")
                .to("direct:updateNeighbouringCountriesInTiamat")
                .to("direct:processTiamatNeighbouringCountriesUpdateCompleted")
                .log(LoggingLevel.INFO, "Finished updating neighbouring countries in Tiamat")
                .doFinally()
                .to("direct:cleanUpLocalDirectory")
                .end()

                .routeId("tiamat-neighbouring-countries-update");

        from("direct:fetchNeighbouringCountries")
                .log(LoggingLevel.DEBUG, getClass().getName(), "Fetching latest neighbouring countries ...")

                .process(e -> e.getIn().setBody(blobStoreService.listBlobsInFolder(blobStoreSubdirectory, e).getFiles().stream().filter(f -> f.getName().endsWith("geojson")).collect(Collectors.toList())))
                .split().body()
                .setHeader(Constants.FILE_HANDLE, simple("${body.name}"))
                .process(e -> e.getIn().setHeader(Exchange.FILE_NAME, Paths.get(e.getIn().getBody(BlobStoreFiles.File.class).getName()).getFileName()))
                .to("direct:getBlob")
                .to("file:" + localWorkingDirectory)
                .routeId("tiamat-fetch-neighbouring-countries");

        from("direct:mapNeighbouringCountriesToNetex")
                .log(LoggingLevel.DEBUG, getClass().getName(), "Mapping latest neighbouring countries to Netex ...")
                .process(e -> topographicPlaceConverter.toNetexFile(
                        new GeoJsonSingleTopographicPlaceReader(wrapperFactory, getGeojsonCountryFiles()),
                        localWorkingDirectory + "/neighbouring-countries-netex.xml"))
                .process(e -> e.getIn().setBody(new File(localWorkingDirectory + "/neighbouring-countries-netex.xml")))
                .routeId("tiamat-map-neighbouring-countries-to-netex");

        from("direct:updateNeighbouringCountriesInTiamat")
                .setHeader(Exchange.HTTP_METHOD, constant(org.apache.camel.component.http.HttpMethods.POST))
                .setHeader(Exchange.CONTENT_TYPE, simple(MediaType.APPLICATION_XML))
                .process("authorizationHeaderProcessor")
                .to(tiamatUrl + tiamatPublicationDeliveryPath)
                .routeId("tiamat-neighbouring-countries-update-start");


        from("direct:processTiamatNeighbouringCountriesUpdateCompleted")
                .setProperty(GeoCoderConstants.GEOCODER_NEXT_TASK, constant(GeoCoderConstants.TIAMAT_EXPORT_START))
                .routeId("tiamat-neighbouring-countries-update-completed");
    }

    private File[] getGeojsonCountryFiles() {
        return FileUtils.listFiles(new File(localWorkingDirectory), new String[]{"geojson"}, false).stream().toArray(File[]::new);
    }
}
