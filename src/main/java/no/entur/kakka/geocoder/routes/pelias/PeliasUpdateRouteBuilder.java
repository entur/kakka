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


import no.entur.kakka.geocoder.BaseRouteBuilder;
import no.entur.kakka.geocoder.GeoCoderConstants;
import no.entur.kakka.geocoder.routes.control.GeoCoderTaskType;
import no.entur.kakka.geocoder.routes.util.ExtendedKubernetesService;
import no.entur.kakka.routes.status.JobEvent;
import org.apache.camel.ExchangePattern;
import org.apache.camel.LoggingLevel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import static no.entur.kakka.Constants.JOB_STATUS_ROUTING_DESTINATION;
import static no.entur.kakka.geocoder.GeoCoderConstants.GEOCODER_NEXT_TASK;
import static no.entur.kakka.geocoder.GeoCoderConstants.GEOCODER_RESCHEDULE_TASK;
import static no.entur.kakka.geocoder.GeoCoderConstants.PELIAS_UPDATE_START;

@Component
public class PeliasUpdateRouteBuilder extends BaseRouteBuilder {

    private static final String NO_OF_REPLICAS = "RutebankenESNoOfReplicas";
    /**
     * One time per 24H on MON-FRI
     */
    @Value("${pelias.update.cron.schedule:0+0+23+?+*+MON-FRI}")
    private String cronSchedule;
    // Whether or not  to start a new es-scratch instance. Should only be set to false for local testing.
    @Value("${elasticsearch.scratch.start.new:true}")
    private boolean startNewEsScratch;
    @Autowired
    private PeliasUpdateStatusService updateStatusService;
    @Autowired
    private ExtendedKubernetesService extendedKubernetesService;

    @Override
    public void configure() throws Exception {
        super.configure();

        singletonFrom("quartz://kakka/peliasUpdate?cron=" + cronSchedule + "&trigger.timeZone=Europe/Oslo")
                .autoStartup("{{pelias.update.autoStartup:false}}")
                .filter(e -> shouldQuartzRouteTrigger(e,cronSchedule))
                .log(LoggingLevel.INFO, "Quartz triggers download of administrative units.")
                .setBody(constant(PELIAS_UPDATE_START))
                .to(ExchangePattern.InOnly, "direct:geoCoderStart")
                .routeId("pelias-update-quartz");

        from(PELIAS_UPDATE_START.getEndpoint())
                .log(LoggingLevel.INFO, "Start updating Pelias")
                .bean(updateStatusService, "setBuilding")
                .process(e -> JobEvent.systemJobBuilder(e).startGeocoder(GeoCoderTaskType.PELIAS_UPDATE).build()).to("direct:updateStatus")

                .choice()
                .when(constant(startNewEsScratch))
                .to("direct:startElasticsearchScratchInstance")
                .otherwise()
                .log(LoggingLevel.WARN, "Updating an existing es-scratch instance. Only for local testing!")
                .to("direct:insertElasticsearchIndexData")
                .routeId("pelias-upload");

        from("direct:startElasticsearchScratchInstance")
                .to("direct:getElasticsearchScratchStatus")

                .choice()
                .when(simple("${body} > 0"))
                // Shutdown if already running
                .log(LoggingLevel.INFO, "Elasticsearch scratch instance already running. Scaling down first.")
                .setHeader(JOB_STATUS_ROUTING_DESTINATION, constant("direct:startElasticsearchScratchInstance"))
                .to("direct:shutdownElasticsearchScratchInstance")
                .otherwise()
                .setHeader(NO_OF_REPLICAS, constant(1))
                .setHeader(JOB_STATUS_ROUTING_DESTINATION, constant("direct:insertElasticsearchIndexData"))
                .to("direct:rescaleElasticsearchScratchInstance")
                .end()

                .routeId("pelias-es-scratch-start");


        from("direct:insertElasticsearchIndexDataCompleted")
                .setHeader(JOB_STATUS_ROUTING_DESTINATION, constant("direct:buildElasticsearchImage"))
                .setProperty(GEOCODER_NEXT_TASK, constant(GeoCoderConstants.PELIAS_ES_SCRATCH_STOP))
                .routeId("pelias-es-index-complete");


        from("direct:insertElasticsearchIndexDataFailed")
                .bean(updateStatusService, "setIdle")
                .process(e -> JobEvent.systemJobBuilder(e).state(JobEvent.State.FAILED).build()).to("direct:updateStatus")
                .routeId("pelias-es-index-failed");

        from("direct:shutdownElasticsearchScratchInstance")
                .setHeader(NO_OF_REPLICAS, constant(0))
                .to("direct:rescaleElasticsearchScratchInstance")
                .routeId("pelias-es-scratch-shutdown");


        from("direct:rescaleElasticsearchScratchInstance")
                .log(LoggingLevel.INFO, "Scaling Elasticsearch scratch to ${header." + NO_OF_REPLICAS + "} replicas")
                .choice()
                .when(simple("${header." + NO_OF_REPLICAS + "} > 0"))
                .bean(extendedKubernetesService, "scaleUpDeployment")
                .setProperty(GEOCODER_NEXT_TASK, constant(GeoCoderConstants.PELIAS_ES_SCRATCH_STATUS_POLL))
                .otherwise()
                .bean(extendedKubernetesService, "scaleDownDeployment")
                .setProperty(GEOCODER_NEXT_TASK, constant(GeoCoderConstants.PELIAS_ES_SCRATCH_STATUS_POLL))
                .routeId("pelias-es-scratch-rescale");

        from("direct:pollElasticsearchScratchStatus")
                .bean(extendedKubernetesService, "getNoOfAvailableReplicas")
                .log(LoggingLevel.DEBUG, "number of running replicas: ${body} ")
                .choice()
                .when(simple("${body} == ${header." + NO_OF_REPLICAS + "}"))
                .toD("${header." + JOB_STATUS_ROUTING_DESTINATION + "}")
                .otherwise()
                .setProperty(GEOCODER_RESCHEDULE_TASK, constant(true))
                .end()
                .routeId("pelias-es-scratch-status-poll");

        from("direct:getElasticsearchScratchStatus")
                .setBody(constant(null))
                .bean(extendedKubernetesService, "getNoOfAvailableReplicas")
                .routeId("pelias-es-scratch-status");


        from("direct:buildElasticsearchImage")
                .log(LoggingLevel.DEBUG, "Creating a job es-build-job to upload es data in gcs")
                .bean(extendedKubernetesService, "startESDataUploadJob")
                .to("direct:processPeliasDeployCompleted")
                .routeId("pelias-es-build");

        from("direct:processPeliasDeployCompleted")
                .log(LoggingLevel.INFO, "Finished updating pelias")
                .process(e -> JobEvent.systemJobBuilder(e).state(JobEvent.State.OK).build()).to("direct:updateStatus")
                .bean(updateStatusService, "setIdle")
                .routeId("pelias-deploy-completed");


    }


}
