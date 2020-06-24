package no.entur.kakka.geocoder.export;

import no.entur.kakka.Constants;
import no.entur.kakka.geocoder.BaseRouteBuilder;
import no.entur.kakka.geocoder.geojson.GeojsonFeatureWrapperFactory;
import no.entur.kakka.geocoder.nabu.rest.AdministrativeZone;
import no.entur.kakka.geocoder.netex.TopographicPlaceAdapter;
import no.entur.kakka.geocoder.sosi.SosiElementWrapperFactory;
import no.entur.kakka.geocoder.sosi.SosiTopographicPlaceAdapterReader;
import no.entur.kakka.routes.file.ZipFileUtils;
import no.entur.kakka.security.TokenService;
import no.entur.kakka.services.BlobStoreService;
import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.apache.camel.model.dataformat.JsonLibrary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.wololo.jts2geojson.GeoJSONWriter;

import java.io.File;
import java.io.InputStream;
import java.util.stream.Collectors;

/**
 * Not part of the geocoder, but placed here as it reuses most of the code from the corresponding Tiamat route.
 */
@Component
public class AdministrativeZonesExportRouteBuilder  extends BaseRouteBuilder {

    public final static Logger logger= LoggerFactory.getLogger(AdministrativeZonesExportRouteBuilder.class);

    @Value("${kartverket.blobstore.subdirectory:kartverket}")
    private String blobStoreSubdirectoryForKartverket;

    @Value("${tiamat.administrative.units.update.directory:files/export/adminUnits}")
    private String localWorkingDirectory;

    @Value("${kartverket.admin.units.archive.filename:county/Basisdata_0000_Norge_25833_Fylker_SOSI.zip}")
    private String countiesArchiveFileName;

    @Value("${kartverket.admin.units.filename:Basisdata_0000_Norge_25833_Fylker_SOSI.sos}")
    private String countiesFileName;

    @Value("${kartverket.admin.units.archive.filename:municipality/Basisdata_0000_Norge_25833_Kommuner2020_SOSI.zip}")
    private String municipalitiesArchiveFileName;

    @Value("${kartverket.admin.units.filename:Basisdata_0000_Norge_25833_Kommuner2020_SOSI.sos}")
    private String municipalitiesFileName;

    @Value("${organisation.registry.admin.zone.code.space.id:rb}")
    private String adminZoneCodeSpaceId;
    @Value("${organisation.registry.admin.zone.code.space.xmlns:RB}")
    private String adminZoneCodeSpaceXmlns;

    private GeoJSONWriter geoJSONWriter = new GeoJSONWriter();

    @Autowired
    private TokenService tokenService;

    @Autowired
    private BlobStoreService blobStoreService;

    @Autowired
    private GeojsonFeatureWrapperFactory geoJsonWrapperFactory;

    @Autowired
    private SosiElementWrapperFactory sosiWrapperFactory;


    @Override
    public void configure() throws Exception {
        super.configure();

        from("direct:startAministrativeZonesExport")
                .log(LoggingLevel.INFO, "Starting exports of administrative zones")
                .setHeader(Exchange.FILE_PARENT, constant(localWorkingDirectory))
                .doTry()
                .to("direct:cleanUpLocalDirectory")

                .to("direct:fetchCountiesForExport")
                .to("direct:exportCounties")

                .to("direct:fetchMunicipalitiesForExport")
                .to("direct:exportMunicipalities")

                .log(LoggingLevel.INFO, "Finished export of counties and municipalities")
                .doFinally()
                //.to("direct:cleanUpLocalDirectory")
                .end()
                .routeId("export-administrative-units");


        from("direct:fetchCountiesForExport")
                .log(LoggingLevel.DEBUG, getClass().getName(), "Fetching latest counties ...")
                .setHeader(Constants.FILE_HANDLE, simple(blobStoreSubdirectoryForKartverket + "/administrativeUnits/" + countiesArchiveFileName))
                .to("direct:getBlob")
                .process(e -> ZipFileUtils.unzipFile(e.getIn().getBody(InputStream.class), localWorkingDirectory))
                .routeId("export-fetch-counties-sosi");


        from("direct:exportCounties")
                .process(e -> e.getIn().setBody(new SosiTopographicPlaceAdapterReader(sosiWrapperFactory, new File(localWorkingDirectory + "/" + countiesFileName)).read().stream().map(tpa -> toAdministrativeZone(tpa, "KVE"))
                        .collect(Collectors.toList())))
                .to("direct:exportCountiesToJson")
                .routeId("export-counties");

        from("direct:exportCountiesToJson")
                .split().body()
                .setHeader("privateCode", simple("${body.privateCode}"))
                .marshal().json(JsonLibrary.Jackson)
                .to("file://" + localWorkingDirectory + "/counties.json")
                .end();

        from("direct:fetchMunicipalitiesForExport")
                .log(LoggingLevel.DEBUG, getClass().getName(), "Fetching latest municipalities ...")
                .setHeader(Constants.FILE_HANDLE, simple(blobStoreSubdirectoryForKartverket + "/administrativeUnits/" + municipalitiesArchiveFileName))
                .to("direct:getBlob")
                .process(e -> ZipFileUtils.unzipFile(e.getIn().getBody(InputStream.class), localWorkingDirectory))
                .routeId("export-fetch-municipalities-sosi");

        from("direct:exportMunicipalities")
                .process(e -> e.getIn().setBody(new SosiTopographicPlaceAdapterReader(sosiWrapperFactory, new File(localWorkingDirectory + "/" + municipalitiesFileName)).read().stream().map(tpa -> toAdministrativeZone(tpa, "KVE"))
                        .collect(Collectors.toList())))
                .to("direct:exportMunicipalitiesToJson")
                .routeId("export-municipalities");

        from("direct:exportMunicipalitiesToJson")
                .split().body()
                .setHeader("privateCode", simple("${body.privateCode}"))
                .marshal().json(JsonLibrary.Jackson)
                .to("file://" + localWorkingDirectory + "/municipalities.json")
                .end();
    }

    private AdministrativeZone toAdministrativeZone(TopographicPlaceAdapter topographicPlaceAdapter, String source) {
        org.locationtech.jts.geom.Geometry geometry = topographicPlaceAdapter.getDefaultGeometry();
        org.wololo.geojson.Geometry targetGeometry = geoJSONWriter.write(geometry);
        return new AdministrativeZone(adminZoneCodeSpaceId, topographicPlaceAdapter.getId(),
                topographicPlaceAdapter.getName(), targetGeometry, toType(topographicPlaceAdapter.getType()), source);
    }


    private AdministrativeZone.AdministrativeZoneType toType(TopographicPlaceAdapter.Type type) {
        switch (type) {
            case COUNTRY:
                return AdministrativeZone.AdministrativeZoneType.COUNTRY;
            case COUNTY:
                return AdministrativeZone.AdministrativeZoneType.COUNTY;
            case LOCALITY:
                return AdministrativeZone.AdministrativeZoneType.LOCALITY;
        }

        return AdministrativeZone.AdministrativeZoneType.CUSTOM;
    }
}
