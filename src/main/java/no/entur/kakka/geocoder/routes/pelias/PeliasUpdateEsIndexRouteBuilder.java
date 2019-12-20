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

package no.entur.kakka.geocoder.routes.pelias;

import com.google.common.collect.Lists;
import no.entur.kakka.Constants;
import no.entur.kakka.exceptions.KakkaException;
import no.entur.kakka.geocoder.GeoCoderConstants;
import no.entur.kakka.geocoder.routes.pelias.kartverket.KartverketAddress;
import no.entur.kakka.geocoder.routes.util.AbortRouteException;
import no.entur.kakka.geocoder.routes.util.MarkContentChangedAggregationStrategy;
import no.entur.kakka.geocoder.sosi.SosiFileFilter;
import no.entur.kakka.geocoder.BaseRouteBuilder;
import no.entur.kakka.routes.file.ZipFileUtils;
import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.apache.camel.Predicate;
import org.apache.camel.builder.PredicateBuilder;
import org.apache.camel.component.http4.HttpMethods;
import org.apache.camel.http.common.HttpOperationFailedException;
import org.apache.camel.model.dataformat.BindyType;
import org.apache.camel.processor.aggregate.AggregationStrategy;
import org.apache.camel.processor.aggregate.UseOriginalAggregationStrategy;
import org.apache.camel.processor.validation.PredicateValidationException;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.io.File;
import java.io.InputStream;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Function;

import static no.entur.kakka.Constants.CONTENT_CHANGED;
import static no.entur.kakka.Constants.FILE_HANDLE;
import static no.entur.kakka.Constants.WORKING_DIRECTORY;
import static org.apache.camel.builder.Builder.exceptionStackTrace;
import static org.apache.commons.io.FileUtils.deleteDirectory;

@Component
public class PeliasUpdateEsIndexRouteBuilder extends BaseRouteBuilder {


    @Value("${elasticsearch.scratch.url:http4://es-scratch:9200}")
    private String elasticsearchScratchUrl;

    @Value("${tiamat.export.blobstore.subdirectory:tiamat/geocoder}")
    private String blobStoreSubdirectoryForTiamatGeoCoderExport;

    @Value("${kartverket.blobstore.subdirectory:kartverket}")
    private String blobStoreSubdirectoryForKartverket;

    @Value("${goecoder.gtfs.blobstore.subdirectory:geocoder/gtfs}")
    private String blobStoreSubdirectoryForGtfsStopPlaces;

    @Value("${pelias.download.directory:files/pelias}")
    private String localWorkingDirectory;

    @Value("${pelias.insert.batch.size:10000}")
    private int insertBatchSize;

    @Value("${pelias.addresses.batch.size:10000}")
    private int addressesBatchSize;

    @Value("#{'${geocoder.place.type.whitelist:tettsted,tettsteddel,tettbebyggelse,bygdelagBygd,grend,boligfelt,industriområde,bydel}'.split(',')}")
    private List<String> placeTypeWhiteList;

    @Autowired
    private PeliasUpdateStatusService updateStatusService;

    @Autowired
    private SosiFileFilter sosiFileFilter;

    private static final String HEADER_EXPAND_ZIP = "EXPAND_ZIP";
    private static final String FILE_EXTENSION = "RutebankenFileExtension";
    private static final String CONVERSION_ROUTE = "RutebankenConversionRoute";

    @Override
    public void configure() throws Exception {
        super.configure();

        from("direct:insertElasticsearchIndexData")
                .bean(updateStatusService, "setBuilding")
                .setHeader(CONTENT_CHANGED, constant(false))
                .setHeader(Exchange.FILE_PARENT, constant(localWorkingDirectory))
                .to("direct:cleanUpLocalDirectory")
                .process(e -> new File(localWorkingDirectory).mkdirs())
                .to("direct:createPeliasIndex")
                .bean("adminUnitRepositoryBuilder", "build")
                .setProperty(GeoCoderConstants.GEOCODER_ADMIN_UNIT_REPO, simple("body"))
                .doTry()
                .multicast(new UseOriginalAggregationStrategy())
                .parallelProcessing()
                .stopOnException()
                .to("direct:insertAdministrativeUnits", "direct:insertAddresses", "direct:insertPlaceNames", "direct:insertTiamatData", "direct:insertGtfsStopPlaceData")
                .end()
                .endDoTry()
                .doCatch(AbortRouteException.class)
                .doFinally()
                .setHeader(Exchange.FILE_PARENT, constant(localWorkingDirectory))
                .to("direct:cleanUpLocalDirectory")
                .end()
                .choice()
                .when(e -> updateStatusService.getStatus() != PeliasUpdateStatusService.Status.ABORT)
                .to("direct:insertElasticsearchIndexDataCompleted")
                .otherwise()
                .log(LoggingLevel.INFO, "Pelias update aborted")
                .to("direct:insertElasticsearchIndexDataFailed")
                .end()

                .routeId("pelias-insert-index-data");

        from("direct:createPeliasIndex")
                .to("direct:deletePeliasIndexIfExist")
                .log(LoggingLevel.INFO, "Creating pelias index")
                .setHeader(Exchange.HTTP_METHOD, constant(org.apache.camel.component.http4.HttpMethods.PUT))
                .setHeader(Exchange.CONTENT_TYPE, constant("application/json; charset=utf-8"))
                .process(e -> e.getIn().setBody(this.getClass().getResourceAsStream("/no/entur/kakka/routes/pelias/create_index.json")))
                .convertBodyTo(String.class)
                .to(elasticsearchScratchUrl + "/pelias")
                .routeId("pelias-create-index");

        from("direct:deletePeliasIndexIfExist")
                .log(LoggingLevel.INFO, "Deleting pelias index if it already exists")
                .setHeader(Exchange.HTTP_METHOD, constant(HttpMethods.DELETE))
                .setBody(constant(null))
                .doTry()
                .to(elasticsearchScratchUrl + "/pelias")
                .log(LoggingLevel.INFO, "Deleted pelias index")
                .doCatch(HttpOperationFailedException.class).onWhen(exchange -> {
            HttpOperationFailedException ex = exchange.getException(HttpOperationFailedException.class);
            return (ex.getStatusCode() == 404);
        })
                .log(LoggingLevel.INFO, "Pelias index did not already exist. Ignoring 404")
                .end()
                .routeId("pelias-delete-index-if-exists");


        from("direct:insertAddresses")
                .log(LoggingLevel.INFO, "Start inserting addresses to ES")
                .setHeader(Exchange.FILE_PARENT, simple(blobStoreSubdirectoryForKartverket + "/addresses"))
                .setHeader(WORKING_DIRECTORY, simple(localWorkingDirectory + "/addresses"))
                .setHeader("dataset", simple("addresses"))
                .setHeader(FILE_EXTENSION, constant("csv"))
                .to("direct:haltIfContentIsMissing")
                .log(LoggingLevel.INFO, "Finished inserting addresses to ES")
                .routeId("pelias-insert-addresses");

        from("direct:insertTiamatData")
                .log(LoggingLevel.INFO, "Start inserting Tiamat data to ES")
                .setHeader(Exchange.FILE_PARENT, simple(blobStoreSubdirectoryForTiamatGeoCoderExport))
                .setHeader(WORKING_DIRECTORY, simple(localWorkingDirectory + "/tiamat"))
                .setHeader(CONVERSION_ROUTE, constant("direct:convertToPeliasCommandsFromTiamat"))
                .setHeader(FILE_EXTENSION, constant("xml"))
                .to("direct:haltIfContentIsMissing")
                .log(LoggingLevel.INFO, "Finished inserting Tiamat data to ES")
                .routeId("pelias-insert-tiamat-data");

        from("direct:insertPlaceNames")
                .log(LoggingLevel.DEBUG, "Start inserting place names to ES")
                .setHeader(Exchange.FILE_PARENT, simple(blobStoreSubdirectoryForKartverket + "/placeNames"))
                .setHeader(WORKING_DIRECTORY, simple(localWorkingDirectory + "/placeNames"))
                .setHeader(CONVERSION_ROUTE, constant("direct:convertToPeliasCommandsFromPlaceNames"))
                .setHeader(FILE_EXTENSION, constant("sos"))
                .to("direct:haltIfContentIsMissing")
                .log(LoggingLevel.DEBUG, "Finished inserting place names to ES")
                .routeId("pelias-insert-place-names");

        from("direct:insertAdministrativeUnits")
                .log(LoggingLevel.DEBUG, "Start inserting administrative units to ES")
                .setHeader(Exchange.FILE_PARENT, simple(blobStoreSubdirectoryForKartverket + "/administrativeUnits"))
                .setHeader(WORKING_DIRECTORY, simple(localWorkingDirectory + "/adminUnits"))
                .setHeader(CONVERSION_ROUTE, constant("direct:convertToPeliasCommandsFromKartverketSOSI"))
                .setHeader(FILE_EXTENSION, constant("sos"))
                .to("direct:haltIfContentIsMissing")
                .log(LoggingLevel.DEBUG, "Finished inserting administrative units to ES")
                .routeId("pelias-insert-admin-units");

        from("direct:insertGtfsStopPlaceData")
                .filter(simple("{{pelias.gtfs.stop.place.enabled:false}}"))
                .log(LoggingLevel.DEBUG, "Start inserting GTFS stop place data to ES")
                .setHeader(Exchange.FILE_PARENT, simple(blobStoreSubdirectoryForGtfsStopPlaces))
                .setHeader(WORKING_DIRECTORY, simple(localWorkingDirectory + "/gtfs"))
                .setHeader(CONVERSION_ROUTE, constant("direct:convertToPeliasCommandsFromGtfsStopPlaces"))
                .setHeader(HEADER_EXPAND_ZIP, constant(Boolean.FALSE))
                .to("direct:haltIfContentIsMissing")
                .log(LoggingLevel.DEBUG, "Finished inserting GTFS stop place data to ES")
                .routeId("pelias-insert-gtfs-stopplace-data");


        from("direct:haltIfContentIsMissing")
                .doTry()
                .to("direct:insertToPeliasFromFilesInFolder")
                .choice()
                .when(e -> updateStatusService.getStatus() != PeliasUpdateStatusService.Status.ABORT)
                .validate(header(Constants.CONTENT_CHANGED).isEqualTo(Boolean.TRUE))
                .end()

                .endDoTry()
                .doCatch(PredicateValidationException.class, KakkaException.class)
                .bean(updateStatusService, "signalAbort")
                .log(LoggingLevel.ERROR, "Elasticsearch scratch index build failed for ${header." + WORKING_DIRECTORY + "}: " + exceptionMessage() + " stacktrace: " + exceptionStackTrace())
                .end()
                .routeId("pelias-insert-halt-if-content-missing");


        from("direct:insertToPeliasFromFilesInFolder")
                .bean("blobStoreService", "listBlobsInFolder")
                .split(simple("${body.files}")).stopOnException()
                .aggregationStrategy(new MarkContentChangedAggregationStrategy())
                .to("direct:haltIfAborted")
                .setHeader(FILE_HANDLE, simple("${body.name}"))
                .to("direct:getBlob")

                .choice()
                .when(PredicateBuilder.and(header(FILE_HANDLE).endsWith(".zip"), header(HEADER_EXPAND_ZIP).isNotEqualTo(Boolean.FALSE)))
                .to("direct:insertToPeliasFromZipArchive")
                .when(PredicateBuilder.and(header("dataset").isEqualTo("addresses")))
                .log(LoggingLevel.INFO,"processing addresses...")
                .to("direct:convertToPeliasCommandsFromLargeAddresses")
                .otherwise()
                .log(LoggingLevel.INFO, "Updating indexes in elasticsearch from file: ${header." + FILE_HANDLE + "}")
                .toD("${header." + CONVERSION_ROUTE + "}")
                .to("direct:invokePeliasBulkCommand")
                .end()
                .routeId("pelias-insert-from-folder");


        from("direct:insertToPeliasFromZipArchive")
                .process(e -> ZipFileUtils.unzipAddressFile(e.getIn().getBody(InputStream.class), e.getIn().getHeader(WORKING_DIRECTORY, String.class)))
                .split().exchange(e -> listFiles(e)).stopOnException()
                .aggregationStrategy(new MarkContentChangedAggregationStrategy())
                .to("direct:haltIfAborted")
                .log(LoggingLevel.INFO, "Updating indexes in elasticsearch from file: ${body.name}")
                .choice()
                .when(PredicateBuilder.and(header("dataset").isEqualTo("addresses")))
                .log(LoggingLevel.INFO,"processing zip address file ...")
                .to("direct:convertToPeliasCommandsFromLargeAddresses")
                .otherwise()
                .toD("${header." + CONVERSION_ROUTE + "}")
                .to("direct:invokePeliasBulkCommand")
                .end()
                .process(e -> deleteDirectory(new File(e.getIn().getHeader(WORKING_DIRECTORY, String.class))))
                .routeId("pelias-insert-from-zip");

        from("direct:convertToPeliasCommandsFromKartverketSOSI")
                .bean("kartverketSosiStreamToElasticsearchCommands", "transform")
                .routeId("pelias-convert-commands-kartverket-sosi");

        from("direct:convertToPeliasCommandsFromPlaceNames")
                .process(e -> filterSosiFile(e))
                .bean("kartverketSosiStreamToElasticsearchCommands", "transform")
                .routeId("pelias-convert-commands-place_names");


        from("direct:convertToPeliasCommandsFromGtfsStopPlaces")
                .bean("gtfsStopPlaceStreamToElasticsearchCommands", "transform")
                .routeId("pelias-convert-commands-gtfs-stop-places");

        from("direct:convertToPeliasCommandsFromLargeAddresses")
                .log("Start processing large addresses file ....")
                .split()
                .tokenize("\n",addressesBatchSize)
                .streaming()
                .aggregationStrategy(new MarkContentChangedAggregationStrategy())
                .log("Batch counter: converted ${header.CamelSplitIndex}++  addresses")
                .bean("addressStreamToElasticSearchCommands", "transform")
                .to("direct:invokePeliasBulkCommand")
                .log("End with large address file ....")
                .end();

        from("direct:convertToPeliasCommandsFromTiamat")
                .log(LoggingLevel.INFO,"Transform deliveryPublicationStream To Elasticsearch Commands")
                .bean("deliveryPublicationStreamToElasticsearchCommands", "transform")
                .log(LoggingLevel.INFO,"Transform deliveryPublicationStream To Elasticsearch Commands completed")
                .routeId("pelias-convert-commands-from-tiamat");


        from("direct:invokePeliasBulkCommand")
                .bean("peliasIndexValidCommandFilter")
                .bean("peliasIndexParentInfoEnricher")
                .setHeader(Exchange.HTTP_METHOD, constant(org.apache.camel.component.http4.HttpMethods.POST))
                .setHeader(Exchange.CONTENT_TYPE, constant("application/json; charset=utf-8"))
                .split().exchange(e ->
                                          Lists.partition(e.getIn().getBody(List.class), insertBatchSize)).stopOnException()
                .aggregationStrategy(new MarkContentChangedAggregationStrategy())
                .to("direct:haltIfAborted")
                .bean("elasticsearchCommandWriterService")
                .log(LoggingLevel.INFO, "Adding batch of indexes to elasticsearch for ${header." + FILE_HANDLE + "}")
                .toD(elasticsearchScratchUrl + "/_bulk")
                .setHeader(CONTENT_CHANGED, constant(true))                // TODO parse response?
                .log(LoggingLevel.INFO, "Finished adding batch of indexes to elasticsearch for ${header." + FILE_HANDLE + "}")

                .routeId("pelias-invoke-bulk-command");

        from("direct:haltIfAborted")
                .choice()
                .when(e -> updateStatusService.getStatus() == PeliasUpdateStatusService.Status.ABORT)
                .log(LoggingLevel.DEBUG, "Stopping route because status is ABORT")
                .throwException(new AbortRouteException("Route has been aborted"))
                .end()
                .routeId("pelias-halt-if-aborted");
    }


    private Collection<File> listFiles(Exchange e) {
        String fileExtension = e.getIn().getHeader(FILE_EXTENSION, String.class);
        String directory = e.getIn().getHeader(WORKING_DIRECTORY, String.class);
        return FileUtils.listFiles(new File(directory), new String[]{fileExtension}, true);
    }

    // Create a new Sosi file with only certain types. File with place names is huge and parser does not support streaming.
    private void filterSosiFile(Exchange e) {
        String filteredFile = localWorkingDirectory + "/filtered_place_name.sos";
        sosiFileFilter.filterElements(e.getIn().getBody(InputStream.class), filteredFile, sosiMatcher);
        e.getIn().setBody(new File(filteredFile));
    }


    Function<Pair<String, String>, Boolean> sosiMatcher = kv -> {
        if (!"NAVNEOBJEKTTYPE".equals(kv.getKey())) {
            return false;
        }
        if (CollectionUtils.isEmpty(placeTypeWhiteList)) {
            return true;
        }

        return kv.getValue() != null && placeTypeWhiteList.contains(kv.getValue());
    };

}
