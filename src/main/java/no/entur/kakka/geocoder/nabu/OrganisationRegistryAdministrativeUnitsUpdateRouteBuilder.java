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

package no.entur.kakka.geocoder.nabu;

import no.entur.kakka.Constants;
import no.entur.kakka.Utils;
import no.entur.kakka.domain.BlobStoreFiles;
import no.entur.kakka.geocoder.BaseRouteBuilder;
import no.entur.kakka.geocoder.geojson.GeojsonFeatureWrapperFactory;
import no.entur.kakka.geocoder.nabu.rest.AdministrativeZone;
import no.entur.kakka.geocoder.netex.TopographicPlaceAdapter;
import no.entur.kakka.geocoder.netex.geojson.GeoJsonSingleTopographicPlaceReader;
import no.entur.kakka.geocoder.sosi.SosiElementWrapperFactory;
import no.entur.kakka.geocoder.sosi.SosiTopographicPlaceAdapterReader;
import no.entur.kakka.routes.file.ZipFileUtils;
import no.entur.kakka.services.BlobStoreService;
import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.apache.camel.http.common.HttpMethods;
import org.apache.camel.http.base.HttpOperationFailedException;
import org.apache.camel.model.dataformat.JsonLibrary;
import org.apache.commons.io.FileUtils;
import org.locationtech.jts.geom.CoordinateList;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.wololo.geojson.Polygon;
import org.wololo.jts2geojson.GeoJSONWriter;

import jakarta.ws.rs.core.MediaType;
import java.io.File;
import java.io.InputStream;
import java.nio.file.Paths;
import java.util.stream.Collectors;

/**
 * Update admin units in organisation registry. Not part of the geocoder, but placed here as it reuses most of the code from the corresponding Tiamat route.
 */
@Component
public class OrganisationRegistryAdministrativeUnitsUpdateRouteBuilder extends BaseRouteBuilder {


    private final GeoJSONWriter geoJSONWriter = new GeoJSONWriter();
    @Value("${kartverket.blobstore.subdirectory:kartverket}")
    private String blobStoreSubdirectoryForKartverket;
    @Value("${tiamat.countries.geojson.blobstore.subdirectory:geojson/countries}")
    private String blobStoreSubdirectoryCountries;
    @Value("${organisations.api.url:http://baba/services/organisations/}")
    private String organisationRegistryUrl;
    @Value("${tiamat.administrative.units.update.directory:files/orgReg/adminUnits}")
    private String localWorkingDirectory;
    @Value("${kartverket.admin.units.archive.filename:county/Basisdata_0000_Norge_25833_Fylker_SOSI.zip}")
    private String adminUnitsArchiveFileName;
    @Value("${kartverket.admin.units.filename:Basisdata_0000_Norge_25833_Fylker_SOSI.sos}")
    private String adminUnitsFileName;
    @Value("${organisation.registry.admin.zone.code.space.id:rb}")
    private String adminZoneCodeSpaceId;
    @Value("${organisation.registry.admin.zone.code.space.xmlns:RB}")
    private String adminZoneCodeSpaceXmlns;
    @Autowired
    private BlobStoreService blobStoreService;

    @Autowired
    private GeojsonFeatureWrapperFactory geoJsonWrapperFactory;

    @Autowired
    private SosiElementWrapperFactory sosiWrapperFactory;

    @Override
    public void configure() throws Exception {
        super.configure();

        from("direct:updateAdminUnitsInOrgReg")
                .log(LoggingLevel.INFO, "Starting update of administrative units in Organisation registry")
                .setHeader(Exchange.FILE_PARENT, constant(localWorkingDirectory))
                .doTry()
                .to("direct:cleanUpLocalDirectory")

                .to("direct:fetchNeighbouringCountriesForOrgReg")
                .to("direct:updateNeighbouringCountriesInOrgReg")

                .to("direct:fetchAdministrativeUnitsForOrgReg")
                .to("direct:updateAdministrativeUnitsInOrgReg")

                .log(LoggingLevel.INFO, "Finished update of administrative units in Organisation registry")
                .doFinally()
                .to("direct:cleanUpLocalDirectory")
                .end()
                .routeId("organisation-registry-update-administrative-units");


        from("direct:fetchAdministrativeUnitsForOrgReg")
                .log(LoggingLevel.DEBUG, getClass().getName(), "Fetching latest administrative units ...")
                .setHeader(Constants.FILE_HANDLE, simple(blobStoreSubdirectoryForKartverket + "/administrativeUnits/" + adminUnitsArchiveFileName))
                .to("direct:getBlob")
                .process(e -> ZipFileUtils.unzipFile(e.getIn().getBody(InputStream.class), localWorkingDirectory))
                .routeId("organisation-registry-fetch-admin-units-sosi");

        from("direct:fetchNeighbouringCountriesForOrgReg")
                .log(LoggingLevel.DEBUG, getClass().getName(), "Fetching latest neighbouring countries ...")

                .process(e -> e.getIn().setBody(blobStoreService.listBlobsInFolder(blobStoreSubdirectoryCountries, e).getFiles().stream().filter(f -> f.getName().endsWith("geojson")).collect(Collectors.toList())))
                .split().body()
                .setHeader(Constants.FILE_HANDLE, simple("${body.name}"))
                .process(e -> e.getIn().setHeader(Exchange.FILE_NAME, Paths.get(e.getIn().getBody(BlobStoreFiles.File.class).getName()).getFileName()))
                .to("direct:getBlob")
                .to("file:" + localWorkingDirectory)
                .routeId("organisation-registry-fetch-neighbouring-countries-geojson");


        from("direct:updateNeighbouringCountriesInOrgReg")
                .log(LoggingLevel.DEBUG, getClass().getName(), "Mapping latest neighbouring countries to org reg format ...")
                .process(e -> e.getIn().setBody(new GeoJsonSingleTopographicPlaceReader(geoJsonWrapperFactory, getGeojsonCountryFiles()).read().stream().map(tpa -> toAdministrativeZone(tpa, "WOF")).collect(Collectors.toList())))
                .to("direct:updateAdministrativeZonesInOrgReg")
                .routeId("organisation-registry-update-neighbouring-countries");

        from("direct:updateAdministrativeUnitsInOrgReg")
                .process(e -> e.getIn().setBody(new SosiTopographicPlaceAdapterReader(sosiWrapperFactory, new File(localWorkingDirectory + "/" + adminUnitsFileName)).read().stream().map(tpa -> toAdministrativeZone(tpa, "KVE"))
                        .collect(Collectors.toList())))
                .to("direct:updateAdministrativeZonesInOrgReg")
                .routeId("organisation-registry-update-admin-units");


        from("direct:updateAdministrativeZonesInOrgReg")
                .split().body()
                .setHeader("privateCode", simple("${body.privateCode}"))
                .marshal().json(JsonLibrary.Jackson)
                .setHeader(Exchange.HTTP_METHOD, constant(org.apache.camel.component.http.HttpMethods.POST))
                .setHeader(Exchange.CONTENT_TYPE, simple(MediaType.APPLICATION_JSON))
                .process("authorizationHeaderProcessor")
                .doTry()
                .toD(getOrganisationRegistryUrl() + "administrative_zones")

                .doCatch(HttpOperationFailedException.class).onWhen(exchange -> {
                    HttpOperationFailedException ex = exchange.getException(HttpOperationFailedException.class);
                    return (ex.getStatusCode() == HttpStatus.CONFLICT.value());
                })  // Update if zone already exists
                .setHeader(Exchange.HTTP_METHOD, constant(HttpMethods.PUT))
                .toD(getOrganisationRegistryUrl() + "administrative_zones/" + adminZoneCodeSpaceXmlns + ":AdministrativeZone:${header.privateCode}")
                .end()

                .routeId("organisation-registry-map-to-admin-zones");


    }


    private String getOrganisationRegistryUrl() {
        return Utils.getHttp4(organisationRegistryUrl);
    }

    private File[] getGeojsonCountryFiles() {
        return FileUtils.listFiles(new File(localWorkingDirectory), new String[]{"geojson"}, false).toArray(File[]::new);
    }

    private AdministrativeZone toAdministrativeZone(TopographicPlaceAdapter topographicPlaceAdapter, String source) {

        org.locationtech.jts.geom.Geometry geometry = topographicPlaceAdapter.getDefaultGeometry();

        if (geometry instanceof org.locationtech.jts.geom.MultiPolygon) {
            CoordinateList coordinateList = new CoordinateList(geometry.getBoundary().getCoordinates());
            coordinateList.closeRing();
            geometry = geometry.getFactory().createPolygon(coordinateList.toCoordinateArray());
        }

        Polygon geoJsonPolygon = (Polygon) geoJSONWriter.write(geometry);
        return new AdministrativeZone(adminZoneCodeSpaceId, topographicPlaceAdapter.getId(),
                topographicPlaceAdapter.getName(), geoJsonPolygon, toType(topographicPlaceAdapter.getType()), source);
    }


    private AdministrativeZone.AdministrativeZoneType toType(TopographicPlaceAdapter.Type type) {
        return switch (type) {
            case COUNTRY -> AdministrativeZone.AdministrativeZoneType.COUNTRY;
            case COUNTY -> AdministrativeZone.AdministrativeZoneType.COUNTY;
            case LOCALITY -> AdministrativeZone.AdministrativeZoneType.LOCALITY;
            default -> AdministrativeZone.AdministrativeZoneType.CUSTOM;
        };

    }
}
