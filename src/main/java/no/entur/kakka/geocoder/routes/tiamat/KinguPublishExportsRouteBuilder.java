package no.entur.kakka.geocoder.routes.tiamat;

import no.entur.kakka.Constants;
import no.entur.kakka.geocoder.BaseRouteBuilder;
import no.entur.kakka.services.TaskGenerator;
import org.apache.camel.LoggingLevel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class KinguPublishExportsRouteBuilder extends BaseRouteBuilder {

    @Value("${tiamat.publish.export.cron.schedule:0+0+23+*+*+?}")
    private String cronSchedule;

    @Value("${kingu.outgoing.camel.route.topic.netex.export}")
    private String outGoingNetexExport;

    @Value("${blobstore.gcs.source.container.name}")
    private String sourceContainerName;

    @Value("${tiamat.publish.export.source.blobstore.subdirectory:export}")
    private String blobStoreSourceSubdirectoryForTiamatExport;

    @Value("${tiamat.publish.export.blobstore.subdirectory:tiamat}")
    private String blobStoreSubdirectoryForTiamatExport;

    @Autowired
    TaskGenerator taskGenerator;

    @Override
    public void configure() throws Exception {
        super.configure();

        singletonFrom("quartz2://kakka/kinguPublishExport?cron=" + cronSchedule + "&trigger.timeZone=Europe/Oslo")
                .autoStartup("{{kingu.export.autoStartup:false}}")
                .filter(e -> isSingletonRouteActive(e.getFromRouteId()))
                .log(LoggingLevel.INFO, "Quartz triggers Kingu exports for publish ")
                .inOnly("direct:startFullKinguPublishExport")
                .routeId("kingu-publish-export-quartz");


        from("direct:startFullKinguPublishExport")
                .log(LoggingLevel.INFO, "Starting Tiamat export")
                .process(e -> taskGenerator.addExportTasks(e))
                .routeId("kingu-publish-export-start-full");

        from(outGoingNetexExport)
                .log(LoggingLevel.INFO, "Done processing Tiamat exports: ${body}")
                .log(LoggingLevel.INFO,"Export location is $simple{in.header.exportLocation}")
                .setHeader(Constants.SOURCE_CONTAINER_NAME,simple(sourceContainerName))
                .setHeader(Constants.FILE_HANDLE,simple(blobStoreSourceSubdirectoryForTiamatExport +"/${in.header.exportLocation}"))
                .setHeader(Constants.TARGET_FILE_HANDLE, simple(blobStoreSubdirectoryForTiamatExport + "/03_Oslo_latest.zip"))
                .to("direct:tiamatExportUploadFileExternal")
                .routeId("from-tiamat-export-queue-processed");
    }
}
