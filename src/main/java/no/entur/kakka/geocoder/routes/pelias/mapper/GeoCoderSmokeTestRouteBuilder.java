package no.entur.kakka.geocoder.routes.pelias.mapper;

import no.entur.kakka.Constants;
import no.entur.kakka.exceptions.KakkaException;
import no.entur.kakka.geocoder.BaseRouteBuilder;
import no.entur.kakka.geocoder.routes.util.ExtendedKubernetesService;
import no.entur.kakka.routes.status.JobEvent;
import no.entur.kakka.services.BlobStoreService;
import org.apache.camel.LoggingLevel;
import org.apache.commons.io.IOUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

@Component
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

    @Autowired
    private BlobStoreService blobStoreService;

    public enum Status {SUCCESSFUL, FAILED}

    @Override
    public void configure() throws Exception {
        super.configure();

        singletonFrom(geoCoderSmokeTestQueue)
                .log(LoggingLevel.INFO, "Incoming message from geocoder smoke test queue")
                .choice()
                .when(header(Constants.GEOCODER_SMOKE_TEST_JOB_STATUS).isEqualTo(Status.SUCCESSFUL))
                .when(header(Constants.ES_DATA_PATH).isNotNull())
                .process(e -> JobEvent.systemJobBuilder(e).jobDomain(JobEvent.JobDomain.GEOCODER).action("GEOCODER_SMOKE_TEST").state(JobEvent.State.OK).build()).to("direct:updateStatus")
                .to("direct:redeployPelias")
                .otherwise()
                .log(LoggingLevel.WARN,"Some of geocoder smoke test failed, not redeploying  ")
                .process(e -> JobEvent.systemJobBuilder(e).jobDomain(JobEvent.JobDomain.GEOCODER).action("GEOCODER_SMOKE_TEST").state(JobEvent.State.FAILED).build()).to("direct:updateStatus")
                .end()
                .routeId("geocoder-smoke-test-queue-route");

        from("direct:redeployPelias")
                .filter(constant(redeployPeliasEnabled))
                .doTry()
                    .log(LoggingLevel.DEBUG, "Updating es current file")
                    .process(e -> blobStoreService.uploadBlob(peliasCurrentFilePath,false,generateCurrentFile(e.getIn().getHeader(Constants.ES_DATA_PATH,String.class))))
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

    private InputStream generateCurrentFile(String path) {
        return IOUtils.toInputStream(path, StandardCharsets.UTF_8);
    }
}
