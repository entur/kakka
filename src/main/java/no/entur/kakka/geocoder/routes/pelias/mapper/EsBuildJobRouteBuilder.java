package no.entur.kakka.geocoder.routes.pelias.mapper;

import no.entur.kakka.Constants;
import no.entur.kakka.geocoder.BaseRouteBuilder;
import no.entur.kakka.geocoder.routes.util.ExtendedKubernetesService;
import no.entur.kakka.routes.status.JobEvent;
import org.apache.camel.LoggingLevel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

public class EsBuildJobRouteBuilder extends BaseRouteBuilder {

    @Value("${es.build.job.camel.route.subscription}")
    private String esBuildJobQueue;

    public enum Status {SUCCESSFUL, FAILED}

    @Autowired
    private ExtendedKubernetesService extendedKubernetesService;

    @Override
    public void configure() throws Exception {
        super.configure();

        singletonFrom(esBuildJobQueue)
                .log(LoggingLevel.INFO, "Incoming message from es build job  queue")
                .choice()
                .when(header(Constants.ES_BUILD_JOB_STATUS).isEqualTo(Status.SUCCESSFUL))
                .to("direct:runGeoCoderSmokeTest")
                .otherwise()
                .log(LoggingLevel.WARN,"ES build job was not successful. ")
                .process(e -> JobEvent.systemJobBuilder(e).jobDomain(JobEvent.JobDomain.GEOCODER).action("ES_BUILD_JOB").state(JobEvent.State.FAILED).build()).to("direct:updateStatus")
                .end()
                .routeId("from-es-build-job-queue");

        from("direct:runGeoCoderSmokeTest")
                .log(LoggingLevel.DEBUG, "Creating a job es-build-job to upload es data in gcs")
                .choice()
                .bean(extendedKubernetesService, "startGeoCoderSmokeTestJob")
                .to("direct:processPeliasDeployCompleted")
                .routeId("pelias-es-build");
    }

}