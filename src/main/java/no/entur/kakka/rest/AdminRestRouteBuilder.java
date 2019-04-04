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

import no.entur.kakka.domain.CustomConfiguration;
import no.entur.kakka.geocoder.BaseRouteBuilder;
import no.entur.kakka.geocoder.routes.control.GeoCoderTaskType;
import no.entur.kakka.security.AuthorizationService;
import org.apache.camel.Exchange;
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
import java.util.Set;
import java.util.stream.Collectors;

/**
 * REST interface for backdoor triggering of messages
 */
@Component
public class AdminRestRouteBuilder extends BaseRouteBuilder {

    private static final String PLAIN = "text/plain";

    @Value("${server.admin.port:8080}")
    public String port;

    @Value("${server.admin.host:0.0.0.0}")
    public String host;

    @Autowired
    private AuthorizationService authorizationService;

    @Override
    public void configure() throws Exception {
        super.configure();

        RestPropertyDefinition corsAllowedHeaders = new RestPropertyDefinition();
        corsAllowedHeaders.setKey("Access-Control-Allow-Headers");
        corsAllowedHeaders.setValue("Origin, Accept, X-Requested-With, Content-Type, Access-Control-Request-Method, Access-Control-Request-Headers, Authorization");

        restConfiguration().setCorsHeaders(Collections.singletonList(corsAllowedHeaders));


        onException(AccessDeniedException.class)
                .handled(true)
                .setHeader(Exchange.HTTP_RESPONSE_CODE, constant(403))
                .setHeader(Exchange.CONTENT_TYPE, constant("text/plain"))
                .transform(exceptionMessage());

        onException(NotAuthenticatedException.class)
                .handled(true)
                .setHeader(Exchange.HTTP_RESPONSE_CODE, constant(401))
                .setHeader(Exchange.CONTENT_TYPE, constant("text/plain"))
                .transform(exceptionMessage());

        onException(NotFoundException.class)
                .handled(true)
                .setHeader(Exchange.HTTP_RESPONSE_CODE, constant(404))
                .setHeader(Exchange.CONTENT_TYPE, constant("text/plain"))
                .transform(exceptionMessage());

        restConfiguration()
                .component("jetty")
                .bindingMode(RestBindingMode.json)
                .endpointProperty("filtersRef", "keycloakPreAuthActionsFilter,keycloakAuthenticationProcessingFilter")
                .endpointProperty("sessionSupport", "true")
                .endpointProperty("matchOnUriPrefix", "true")
                .endpointProperty("enablemulti-partFilter", "true")
                .enableCORS(true)
                .dataFormatProperty("prettyPrint", "true")
                .host(host)
                .port(port)
                .apiContextPath("/swagger.json")
                .apiProperty("api.title", "Kakka Admin API").apiProperty("api.version", "1.0")
                .contextPath("/services");

        rest("")
                .apiDocs(false)
                .description("Wildcard definitions necessary to get Jetty to match authorization filters to endpoints with path params")
                .get().route().routeId("admin-route-authorize-get").throwException(new NotFoundException()).endRest()
                .post().route().routeId("admin-route-authorize-post").throwException(new NotFoundException()).endRest()
                .put().route().routeId("admin-route-authorize-put").throwException(new NotFoundException()).endRest()
                .delete().route().routeId("admin-route-authorize-delete").throwException(new NotFoundException()).endRest();


        String commonApiDocEndpoint = "rest:get:/services/swagger.json?bridgeEndpoint=true";


        rest("/geocoder_admin")
                .post("/idempotentfilter/clean")
                .description("Clean Idempotent repo for downloads")
                .responseMessage().code(200).endResponseMessage()
                .responseMessage().code(500).message("Internal error").endResponseMessage()
                .route().routeId("admin-application-clean-idempotent-download-repos")
                .process(e -> authorizationService.verifyAtLeastOne(AuthorizationConstants.ROLE_ROUTE_DATA_ADMIN))
                .to("direct:cleanIdempotentDownloadRepo")
                .setBody(constant(null))
                .endRest()

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
                .removeHeaders("CamelHttp*")
                .process(e -> e.getIn().setBody(geoCoderTaskTypesFromString(e.getIn().getHeader("task", Collection.class))))
                .inOnly("direct:geoCoderStartBatch")
                .setBody(constant(null))
                .endRest()

                .get("/swagger.json")
                .apiDocs(false)
                .bindingMode(RestBindingMode.off)
                .route()
                .to(commonApiDocEndpoint)
                .endRest();

        rest("/custom_configurations")
                .description("Custom configuration REST service")
                .consumes("application/json")
                .produces("application/json")

                .get().description("Find all custom configurations").outType(CustomConfiguration[].class)
                .responseMessage().code(200).message("Custom configuration returen successfully").endResponseMessage()
                .to("bean:customConfigurationService?method=findAllCustomConfigurations")

                .get("/{key}").description("Find configuration by key")
                .outType(CustomConfiguration.class)
                .param().name("key").type(RestParamType.path).description("The key of the configuration").dataType("string").endParam()
                .responseMessage().code(200).message("Configuration successfully returned").endResponseMessage()
                .to("bean:customConfigurationService?method=getCustomConfigurationByKey(${header.key})")

                .put("/{key}").description("Update configuration").type(CustomConfiguration.class)
                .param().name("key").type(RestParamType.path).description("The key of the configuration").dataType("string").endParam()
                .param().name("body").type(RestParamType.body).description("The configuration to update").endParam()
                .responseMessage().code(204).message("Configuration successfully update").endResponseMessage()
                .to("direct:update-configuration")

                .post().description("Add new configuration").type(CustomConfiguration.class)
                .param().name("body").type(RestParamType.body).description("new configuraiton").endParam()
                .responseMessage().code(204).message("Configuration successfully update").endResponseMessage()
                .to("direct:add-configuration")

                .delete("/{key}").description("Delete configuration")
                .param().name("key").type(RestParamType.path).description("The key of the configuration").dataType("string").endParam()
                .responseMessage().code(204).message("Configuration successfully deleted").endResponseMessage()
                .to("bean:customConfigurationService?method=deleteCustomConfiguration(${header.key})");





        rest("/organisation_admin")
                .post("/administrative_zones/update")
                .description("Update administrative zones in the organisation registry")
                .consumes(PLAIN)
                .produces(PLAIN)
                .responseMessage().code(200).message("Command accepted").endResponseMessage()
                .route()
                .process(e -> authorizationService.verifyAtLeastOne(AuthorizationConstants.ROLE_ORGANISATION_EDIT))
                .removeHeaders("CamelHttp*")
                .to("direct:updateAdminUnitsInOrgReg")
                .setBody(simple("done"))
                .routeId("admin-org-reg-import-admin-zones")
                .endRest()

                .get("/swagger.json")
                .apiDocs(false)
                .bindingMode(RestBindingMode.off)
                .route()
                .to(commonApiDocEndpoint)
                .endRest();


        rest("/export")
                .post("/stop_places")
                .description("Trigger export from Stop Place Registry (NSR) for all existing configurations")
                .consumes(PLAIN)
                .produces(PLAIN)
                .responseMessage().code(200).message("Command accepted").endResponseMessage()
                .route()
                .process(e -> authorizationService.verifyAtLeastOne(AuthorizationConstants.ROLE_ROUTE_DATA_ADMIN))
                .removeHeaders("CamelHttp*")
                .removeHeaders("Authorization")
                .to("direct:startFullTiamatPublishExport")
                .setBody(simple("done"))
                .routeId("admin-tiamat-publish-export-full")
                .endRest()

                .get("/swagger.json")
                .apiDocs(false)
                .bindingMode(RestBindingMode.off)
                .route()
                .to(commonApiDocEndpoint)
                .endRest();

        from("direct:update-configuration")
                .to("bean:customConfigurationService?method=updateCustomConfiguration")
                .setHeader(Exchange.HTTP_RESPONSE_CODE,constant(204))
                .setBody(constant(""));

        from("direct:add-configuration")
                .to("bean:customConfigurationService?method=saveCustomConfiguration")
                .setHeader(Exchange.HTTP_RESPONSE_CODE,constant(204))
                .setBody(constant(""));

    }

    private Set<GeoCoderTaskType> geoCoderTaskTypesFromString(Collection<String> typeStrings) {
        return typeStrings.stream().map(s -> GeoCoderTaskType.valueOf(s)).collect(Collectors.toSet());
    }


}


