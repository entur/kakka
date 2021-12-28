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

package no.entur.kakka.rest;

import no.entur.kakka.Constants;
import no.entur.kakka.domain.OSMPOIFilter;
import no.entur.kakka.geocoder.BaseRouteBuilder;
import no.entur.kakka.geocoder.routes.control.GeoCoderTaskType;
import no.entur.kakka.security.AuthorizationService;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.LoggingLevel;
import org.apache.camel.model.rest.RestBindingMode;
import org.apache.camel.model.rest.RestParamType;
import org.apache.camel.model.rest.RestPropertyDefinition;
import org.rutebanken.helper.organisation.AuthorizationConstants;
import org.rutebanken.helper.organisation.NotAuthenticatedException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

import javax.ws.rs.NotFoundException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static javax.ws.rs.core.MediaType.MULTIPART_FORM_DATA;

/**
 * REST interface for backdoor triggering of messages
 */
@Component
public class AdminRestRouteBuilder extends BaseRouteBuilder {

    public static final String FILE_HANDLE = "FileHandle";
    private static final String PLAIN = "text/plain";
    private static final String PROVIDER_ID = "ProviderId";
    private static final String JSON = "application/json";
    @Value("${server.port:8080}")
    private String port;

    @Value("${server.host:0.0.0.0}")
    private String host;

    @Autowired
    private AuthorizationService authorizationService;

    @Value("#{'${tariff.zone.providers:RUT,AKT,KOL,OST,VOT,TRO}'.split(',')}")
    private List<String> tariffZoneProviders;

    @Override
    public void configure() throws Exception {

        final String camelHttpPattern = "CamelHttp*";
        final String swaggerJsonPath = "/swagger.json";

        super.configure();

        RestPropertyDefinition corsAllowedHeaders = new RestPropertyDefinition();
        corsAllowedHeaders.setKey("Access-Control-Allow-Headers");
        corsAllowedHeaders.setValue("Origin, Accept, X-Requested-With, Content-Type, Access-Control-Request-Method, Access-Control-Request-Headers, Authorization");

        restConfiguration().setCorsHeaders(Collections.singletonList(corsAllowedHeaders));


        onException(AccessDeniedException.class)
                .handled(true)
                .setHeader(Exchange.HTTP_RESPONSE_CODE, constant(403))
                .setHeader(Exchange.CONTENT_TYPE, constant(PLAIN))
                .transform(exceptionMessage());

        onException(NotAuthenticatedException.class)
                .handled(true)
                .setHeader(Exchange.HTTP_RESPONSE_CODE, constant(401))
                .setHeader(Exchange.CONTENT_TYPE, constant(PLAIN))
                .transform(exceptionMessage());

        onException(NotFoundException.class)
                .handled(true)
                .setHeader(Exchange.HTTP_RESPONSE_CODE, constant(404))
                .setHeader(Exchange.CONTENT_TYPE, constant(PLAIN))
                .transform(exceptionMessage());

        restConfiguration()
                .component("servlet")
                .contextPath("/services")
                .bindingMode(RestBindingMode.json)
                .endpointProperty("matchOnUriPrefix", "true")
                .apiContextPath("/swagger.json")
                .dataFormatProperty("prettyPrint", "true")
                .apiContextPath(swaggerJsonPath)
                .apiProperty("api.title", "Kakka Admin API").apiProperty("api.version", "1.0");

        rest("")
                .apiDocs(false)
                .description("Wildcard definitions necessary to get Jetty to match authorization filters to endpoints with path params")
                .get().route().routeId("admin-route-authorize-get").throwException(new NotFoundException()).endRest()
                .post().route().routeId("admin-route-authorize-post").throwException(new NotFoundException()).endRest()
                .put().route().routeId("admin-route-authorize-put").throwException(new NotFoundException()).endRest()
                .delete().route().routeId("admin-route-authorize-delete").throwException(new NotFoundException()).endRest();


        String commonApiDocEndpoint = "http:" + host + ":" + port + "/services/swagger.json?bridgeEndpoint=true";


        rest("/geocoder_admin")
                .post("/build_pipeline")
                .param().name("task")
                .type(RestParamType.query)
                .allowableValues(Arrays.asList(GeoCoderTaskType.values()).stream().map(GeoCoderTaskType::name).collect(Collectors.toList()))
                .required(Boolean.TRUE)
                .description("Tasks to be executed")
                .endParam()
                .description("Update geocoder tasks")
                .responseMessage().code(200).endResponseMessage()
                .responseMessage().code(500).message("Internal error").endResponseMessage()
                .route().routeId("admin-geocoder-update")
                .process(e -> authorizationService.verifyAtLeastOne(AuthorizationConstants.ROLE_ROUTE_DATA_ADMIN))
                .validate(header("task").isNotNull())
                .removeHeaders(camelHttpPattern)
                .process(e -> e.getIn().setBody(geoCoderTaskTypesFromString(e.getIn().getHeader("task", Collection.class))))
                .to(ExchangePattern.InOnly,"direct:geoCoderStartBatch")
                .setBody(constant(null))
                .endRest()

                .get(swaggerJsonPath)
                .apiDocs(false)
                .bindingMode(RestBindingMode.off)
                .route()
                .to(commonApiDocEndpoint)
                .endRest();


        rest("/osmpoifilter")
                .description("OSM POI Filters REST service")
                .consumes(JSON)
                .produces(JSON)
                .get().description("Get all filters").outType(OSMPOIFilter[].class)
                .responseMessage().code(200).message("Filters returned successfully").endResponseMessage()
                .to("bean:osmpoifilterService?method=getFilters")
                .put().description("Update (replace) all filters").type(OSMPOIFilter[].class)
                .param().name("body").type(RestParamType.body).description("List of filters").endParam()
                .responseMessage().code(200).message("Filters updated successfully").endResponseMessage()
                .route().routeId("poi-filter-v2-delete-route")
                .process(e -> authorizationService.verifyAtLeastOne(AuthorizationConstants.ROLE_ROUTE_DATA_ADMIN))
                .to("bean:osmpoifilterService?method=updateFilters");

        rest("/organisation_admin")
                .post("/administrative_zones/update")
                .description("Update administrative zones in the organisation registry")
                .consumes(PLAIN)
                .produces(PLAIN)
                .responseMessage().code(200).message("Command accepted").endResponseMessage()
                .route()
                .process(e -> authorizationService.verifyAtLeastOne(AuthorizationConstants.ROLE_ORGANISATION_EDIT))
                .removeHeaders(camelHttpPattern)
                .to("direct:updateAdminUnitsInOrgReg")
                .setBody(simple("done"))
                .routeId("admin-org-reg-import-admin-zones")
                .endRest()

                .get(swaggerJsonPath)
                .apiDocs(false)
                .bindingMode(RestBindingMode.off)
                .route()
                .to(commonApiDocEndpoint)
                .endRest();

        rest("/map_admin")
                .post("/download")
                .description("Triggers downloading of the latest OSM data")
                .consumes(PLAIN)
                .produces(PLAIN)
                .responseMessage().code(200).message("Command accepted").endResponseMessage()
                .route()
                .process(e -> authorizationService.verifyAtLeastOne(AuthorizationConstants.ROLE_ROUTE_DATA_ADMIN))
                .log(LoggingLevel.INFO, "OSM update map data")
                .removeHeaders(Constants.CAMEL_ALL_HTTP_HEADERS)
                .to("direct:considerToFetchOsmMapOverNorway")
                .routeId("admin-fetch-osm")
                .endRest();

        rest("/export")
                .post("/stop_places")
                .description("Trigger export from Stop Place Registry (NSR) for all existing configurations")
                .consumes(PLAIN)
                .produces(PLAIN)
                .responseMessage().code(200).message("Command accepted").endResponseMessage()
                .route()
                .process(e -> authorizationService.verifyAtLeastOne(AuthorizationConstants.ROLE_ROUTE_DATA_ADMIN))
                .removeHeaders(camelHttpPattern)
                .removeHeaders("Authorization")
                .to("direct:startFullTiamatPublishExport")
                .setBody(simple("done"))
                .routeId("admin-tiamat-publish-export-full")
                .endRest()

                .get(swaggerJsonPath)
                .apiDocs(false)
                .bindingMode(RestBindingMode.off)
                .route()
                .to(commonApiDocEndpoint)
                .endRest();

        rest("/export")
                .post("/stop_places/v2")
                .description("Trigger export from Kingu(netex exporter) using pub/sub for all existing configurations")
                .consumes(PLAIN)
                .produces(PLAIN)
                .responseMessage().code(200).message("Command accepted").endResponseMessage()
                .route()
                .process(e -> authorizationService.verifyAtLeastOne(AuthorizationConstants.ROLE_ROUTE_DATA_ADMIN))
                .removeHeaders(camelHttpPattern)
                .removeHeaders("Authorization")
                .to("direct:startFullKinguPublishExport")
                .setBody(simple("done"))
                .routeId("admin-tiamat-publish-export-full-v2")
                .endRest();


        rest("/tariff_zone_admin/{providerId}")
                .post("/files")
                .description("Upload tariff zone netex file for import into Tiamat")
                .param().name("providerId").type(RestParamType.path).description("Tariff zone Provider id e.g RUT,AKT,KOL").dataType("string").endParam()
                .consumes(MULTIPART_FORM_DATA)
                .produces(PLAIN)
                .bindingMode(RestBindingMode.off)
                .responseMessage().code(200).endResponseMessage()
                .responseMessage().code(500).message("Invalid providerId").endResponseMessage()
                .route()
                .streamCaching()
                .setHeader(PROVIDER_ID, header("providerId"))
                .process(e -> authorizationService.verifyAtLeastOne(AuthorizationConstants.ROLE_ROUTE_DATA_ADMIN))
                .to("direct:validateProvider")
                .log(LoggingLevel.INFO, "Upload files and start import pipeline")
                .removeHeaders(Constants.CAMEL_ALL_HTTP_HEADERS)
                .to("direct:uploadFilesAndStartImport")
                .routeId("admin-tariff-zone-upload-file")
                .endRest();

        from("direct:validateProvider")
                .validate(e -> tariffZoneProviders.stream().anyMatch(tz -> tz.equals(e.getIn().getHeader(PROVIDER_ID, String.class))))
                .routeId("admin-validate-provider");

    }

    private Set<GeoCoderTaskType> geoCoderTaskTypesFromString(Collection<String> typeStrings) {
        return typeStrings.stream().map(GeoCoderTaskType::valueOf).collect(Collectors.toSet());
    }

}



