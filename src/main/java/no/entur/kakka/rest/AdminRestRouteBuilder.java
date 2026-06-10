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

import no.entur.kakka.geocoder.BaseRouteBuilder;
import no.entur.kakka.geocoder.routes.control.GeoCoderTaskType;
import no.entur.kakka.security.KakkaAuthorizationService;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.model.rest.RestBindingMode;
import org.apache.camel.model.rest.RestParamType;
import org.apache.camel.model.rest.RestPropertyDefinition;
import org.rutebanken.helper.organisation.NotAuthenticatedException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

import jakarta.ws.rs.NotFoundException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * REST API for triggering Tiamat updates/exports and organisation-registry admin-zone imports.
 */
@Component
public class AdminRestRouteBuilder extends BaseRouteBuilder {

    public static final String FILE_HANDLE = "FileHandle";
    private static final String PLAIN = "text/plain";
    @Value("${server.port:8080}")
    private String port;

    @Value("${server.host:0.0.0.0}")
    private String host;

    @Autowired
    private KakkaAuthorizationService kakkaAuthorizationService;

    @Override
    public void configure() throws Exception {

        final String camelHttpPattern = "CamelHttp*";
        final String openApiJsonPath = "/openapi.json";

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
                .apiContextPath("/openapi.json")
                .dataFormatProperty("prettyPrint", "true")
                .apiContextPath(openApiJsonPath)
                .apiProperty("api.title", "Kakka Admin API").apiProperty("api.version", "1.0");

        rest("")
                .apiDocs(false)
                .description("Wildcard definitions necessary to get Jetty to match authorization filters to endpoints with path params")
                .get()
                .to("direct:adminRouteAuthorizeGet")
                .post()
                .to("direct:adminRouteAuthorizePost")
                .put()
                .to("direct:adminRouteAuthorizePut")
                .delete()
                .to("direct:adminRouteAuthorizeDelete");


        String commonApiDocEndpoint = "http:" + host + ":" + port + "/services/openapi.json?bridgeEndpoint=true";


        rest("/geocoder_admin")
                .post("/build_pipeline")
                .param().name("task")
                .type(RestParamType.query)
                .allowableValues(Arrays.stream(GeoCoderTaskType.values()).map(GeoCoderTaskType::name).toList())
                .required(Boolean.TRUE)
                .description("Tasks to be executed")
                .endParam()
                .description("Update geocoder tasks")
                .responseMessage().code(200).endResponseMessage()
                .responseMessage().code(500).message("Internal error").endResponseMessage()
                .to("direct:adminGeoCoderStart")

                .get(openApiJsonPath)
                .apiDocs(false)
                .bindingMode(RestBindingMode.off)
                .to(commonApiDocEndpoint);


        rest("/organisation_admin")
                .post("/administrative_zones/update")
                .description("Update administrative zones in the organisation registry")
                .consumes(PLAIN)
                .produces(PLAIN)
                .responseMessage().code(200).message("Command accepted").endResponseMessage()
                .to("direct:adminOrgRegImportAdminZones")
                .get(openApiJsonPath)
                .apiDocs(false)
                .bindingMode(RestBindingMode.off)
                .to(commonApiDocEndpoint);


        rest("/export")
                .post("/stop_places/v2")
                .description("Trigger export from Kingu(netex exporter) using pub/sub for all existing configurations")
                .consumes(PLAIN)
                .produces(PLAIN)
                .responseMessage().code(200).message("Command accepted").endResponseMessage()
                .to("direct:adminTiamatPublishExportFull");

        from("direct:adminRouteAuthorizeGet")
                .throwException(new NotFoundException())
                .routeId("admin-route-authorize-get");

        from("direct:adminRouteAuthorizePost")
                .throwException(new NotFoundException())
                .routeId("admin-route-authorize-post");

        from("direct:adminRouteAuthorizePut")
                .throwException(new NotFoundException())
                .routeId("admin-route-authorize-put");

        from("direct:adminRouteAuthorizeDelete")
                .throwException(new NotFoundException())
                .routeId("admin-route-authorize-delete");

        from("direct:authorizeAdminRequest")
                .doTry()
                .process(e -> kakkaAuthorizationService.verifyRouteDataAdministratorPrivileges())
                .routeId("admin-authorize-admin-request");

        from("direct:authorizeEditRequest")
                .doTry()
                .process(e -> kakkaAuthorizationService.verifyOrganisationAdministratorPrivileges())
                .routeId("admin-authorize-edit-request");

        from("direct:adminGeoCoderStart")
                .to("direct:authorizeAdminRequest")
                .validate(header("task").isNotNull())
                .removeHeaders(camelHttpPattern)
                .process(e -> e.getIn().setBody(geoCoderTaskTypesFromString(e.getIn().getHeader("task", Collection.class))))
                .to(ExchangePattern.InOnly, "direct:geoCoderStartBatch")
                .setBody(constant(null))
                .routeId("admin-geocoder-start-route");

        from("direct:adminOrgRegImportAdminZones")
                .to("direct:authorizeEditRequest")
                .removeHeaders(camelHttpPattern)
                .to("direct:updateAdminUnitsInOrgReg")
                .setBody(simple("done"))
                .routeId("admin-org-reg-import-admin-zones");

        from("direct:adminTiamatPublishExportFull")
                .to("direct:authorizeAdminRequest")
                .removeHeaders(camelHttpPattern)
                .removeHeaders("Authorization")
                .to("direct:startNetexExport")
                .setBody(simple("done"))
                .routeId("admin-tiamat-publish-export-full-v2");


    }

    private Set<GeoCoderTaskType> geoCoderTaskTypesFromString(Collection<String> typeStrings) {
        return typeStrings.stream().map(GeoCoderTaskType::valueOf).collect(Collectors.toSet());
    }

}



