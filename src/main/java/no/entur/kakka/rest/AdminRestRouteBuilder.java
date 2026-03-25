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
import no.entur.kakka.security.KakkaAuthorizationService;
import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.apache.camel.model.rest.RestBindingMode;
import org.apache.camel.model.rest.RestParamType;
import org.apache.camel.model.rest.RestPropertyDefinition;
import org.rutebanken.helper.organisation.NotAuthenticatedException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

import jakarta.ws.rs.NotFoundException;
import java.util.Collections;
import java.util.List;

import static jakarta.ws.rs.core.MediaType.MULTIPART_FORM_DATA;

/**
 * API endpoint for managing the geocoder data import pipeline.
 */
@Component
public class AdminRestRouteBuilder extends BaseRouteBuilder {

    public static final String FILE_HANDLE = "FileHandle";
    private static final String PLAIN = "text/plain";
    private static final String PROVIDER_ID = "ProviderId";
    @Value("${server.port:8080}")
    private String port;

    @Value("${server.host:0.0.0.0}")
    private String host;

    @Autowired
    private KakkaAuthorizationService kakkaAuthorizationService;

    @Value("#{'${tariff.zone.providers:RUT,AKT,KOL,OST,VOT,TRO}'.split(',')}")
    private List<String> tariffZoneProviders;

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

        rest("/tariff_zone_admin/{providerId}")
                .post("/files")
                .description("Upload tariff zone netex file for import into Tiamat")
                .param().name("providerId").type(RestParamType.path).description("Tariff zone Provider id e.g RUT,AKT,KOL").dataType("string").endParam()
                .consumes(MULTIPART_FORM_DATA)
                .produces(PLAIN)
                .bindingMode(RestBindingMode.off)
                .responseMessage().code(200).endResponseMessage()
                .responseMessage().code(500).message("Invalid providerId").endResponseMessage()
                .to("direct:adminTariffZoneUploadFile");

        from("direct:validateProvider")
                .validate(e -> tariffZoneProviders.stream().anyMatch(tz -> tz.equals(e.getIn().getHeader(PROVIDER_ID, String.class))))
                .routeId("admin-validate-provider");

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

        from("direct:adminTariffZoneUploadFile")
                .setBody(simple("${exchange.getIn().getRequest().getParts()}"))
                .streamCaching()
                .setHeader(PROVIDER_ID, header("providerId"))
                .process(e -> kakkaAuthorizationService.verifyRouteDataAdministratorPrivileges())
                .to("direct:validateProvider")
                .log(LoggingLevel.INFO, "Upload files and start import pipeline")
                .removeHeaders("CamelHttp*")
                .to("direct:uploadFilesAndStartImport")
                .routeId("admin-tariff-zone-upload-file");

    }

}
