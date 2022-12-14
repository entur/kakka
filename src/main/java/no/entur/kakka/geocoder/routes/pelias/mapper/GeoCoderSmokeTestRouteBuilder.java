package no.entur.kakka.geocoder.routes.pelias.mapper;

import no.entur.kakka.Constants;
import no.entur.kakka.exceptions.KakkaException;
import no.entur.kakka.geocoder.BaseRouteBuilder;
import no.entur.kakka.geocoder.routes.util.ExtendedKubernetesService;
import no.entur.kakka.routes.status.JobEvent;
import org.apache.camel.LoggingLevel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

public class GeoCoderSmokeTestRouteBuilder extends BaseRouteBuilder {
    @Value("${geocoder.smoke.test.camel.route.subscription}")
    private String geoCoderSmokeTestQueue;

    @Value("${geocoder.pelias.current.file.path:es-data/current}")
    private String peliasCurrentFilePath;

    @Value("${geocoder.pelias.current.file.path:es-data/geoCoderCurrent}")
    private String getGeoCoderSmokeTestQueueCurrentFilePath;

    @Value("${geocoder.pelias.deploymebt.name:pelias}")
    private String deploymentName;
    @Value("${geocoder.redeploy.pelias.enabled:true}")
    private boolean redeployPeliasEnabled;

    @Autowired
    private ExtendedKubernetesService extendedKubernetesService;

    public enum Status {SUCCESSFUL, FAILED}

    @Override
    public void configure() throws Exception {
        super.configure();

        singletonFrom(geoCoderSmokeTestQueue)
                .log(LoggingLevel.INFO, "Incoming message from geocoder smoke test queue")
                .choice()
                .when(header(Constants.GEOCODER_SMOKE_TEST_JOB_STATUS).isEqualTo(Status.SUCCESSFUL))
                .process(e -> JobEvent.systemJobBuilder(e).jobDomain(JobEvent.JobDomain.GEOCODER).action("GEOCODER_SMOKE_TEST").state(JobEvent.State.OK).build()).to("direct:updateStatus")
                .to("direct:redeployPelias")
                .otherwise()
                .log(LoggingLevel.WARN,"Some of geocoder smoke test failed, not redeploying  ")
                .process(e -> JobEvent.systemJobBuilder(e).jobDomain(JobEvent.JobDomain.GEOCODER).action("GEOCODER_SMOKE_TEST").state(JobEvent.State.FAILED).build()).to("direct:updateStatus")
                .end()
                .routeId("from-geocoder-smoke-test-queue");

        from("direct:redeployPelias")
                .filter(constant(redeployPeliasEnabled))
                .doTry()
                    .log(LoggingLevel.DEBUG, "Updating es current file")
                    .setHeader(Constants.FILE_HANDLE, simple(getGeoCoderSmokeTestQueueCurrentFilePath))
                    .setHeader(Constants.TARGET_FILE_HANDLE, simple(peliasCurrentFilePath))
                    .setHeader(Constants.BLOBSTORE_MAKE_BLOB_PUBLIC,constant(false))
                    .bean("blobStoreService", "copyBlob")
                    .log(LoggingLevel.DEBUG, "Redeploying pelias ")
                    .setHeader(Constants.DEPLOYMENT_NAME, simple(deploymentName))
                    .bean(extendedKubernetesService, "rolloutDeployment")
                    .process(e -> JobEvent.systemJobBuilder(e).jobDomain(JobEvent.JobDomain.GEOCODER).action("PELIAS_REDEPLOY").state(JobEvent.State.OK).build()).to("direct:updateStatus")
                .doCatch(KakkaException.class)
                    .log(LoggingLevel.WARN, "failed to redeploy pelias")
                    .process(e -> JobEvent.systemJobBuilder(e).jobDomain(JobEvent.JobDomain.GEOCODER).action("PELIAS_REDEPLOY").state(JobEvent.State.FAILED).build()).to("direct:updateStatus")
                .end()
                .routeId("redeploy-pelias-es-build");
    }
}
