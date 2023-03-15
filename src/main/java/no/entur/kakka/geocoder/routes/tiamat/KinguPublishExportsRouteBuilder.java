package no.entur.kakka.geocoder.routes.tiamat;

import no.entur.kakka.Constants;
import no.entur.kakka.geocoder.BaseRouteBuilder;
import no.entur.kakka.services.TaskGenerator;
import org.apache.camel.ExchangePattern;
import org.apache.camel.LoggingLevel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import static no.entur.kakka.Constants.BLOBSTORE_MAKE_BLOB_PUBLIC;

@Component
public class KinguPublishExportsRouteBuilder extends BaseRouteBuilder {

    @Autowired
    TaskGenerator taskGenerator;
    @Value("${tiamat.publish.export.cron.schedule:0+0+23+*+*+?}")
    private String cronSchedule;
    @Value("${tiamat.publish.export.cron.schedule.mid.day:0+0+12+*+*+?}")
    private String cronScheduleMidDay;
    @Value("${kingu.outgoing.camel.route.topic.netex.export}")
    private String outGoingNetexExport;
    @Value("${tiamat.publish.export.source.blobstore.subdirectory:export}")
    private String blobStoreSourceSubdirectoryForTiamatExport;
    @Value("${tiamat.publish.export.blobstore.subdirectory:tiamat}")
    private String blobStoreSubdirectoryForTiamatExport;
    @Value("${tiamat.geocoder.export.blobstore.subdirectory:tiamat/geocoder}")
    private String blobStoreSubdirectoryForTiamatGeoCoderExport;

    @Override
    public void configure() throws Exception {
        super.configure();

        singletonFrom("quartz://kakka/kinguPublishExport?cron=" + cronSchedule + "&trigger.timeZone=Europe/Oslo")
                .autoStartup("{{kingu.export.autoStartup:false}}")
                .filter(e -> shouldQuartzRouteTrigger(e,cronSchedule))
                .log(LoggingLevel.INFO, "Quartz triggers Kingu exports for publish ")
                .to(ExchangePattern.InOnly,"direct:startFullKinguPublishExport")
                .routeId("kingu-publish-export-quartz");

        singletonFrom("quartz://kakka/kinguPublishExportMidday?cron=" + cronScheduleMidDay + "&trigger.timeZone=Europe/Oslo")
                .autoStartup("{{kingu.export.mid.day.autoStartup:false}}")
                .filter(e -> shouldQuartzRouteTrigger(e,cronScheduleMidDay))
                .log(LoggingLevel.INFO, "Quartz triggers mid day Kingu exports for publish ")
                .to(ExchangePattern.InOnly,"direct:startFullKinguPublishExport")
                .routeId("kingu-publish-export-mid-day-quartz");


        from("direct:startFullKinguPublishExport")
                .log(LoggingLevel.INFO, "Starting Tiamat export")
                .process(e -> taskGenerator.addExportTasks(e))
                .routeId("kingu-publish-export-start-full");

        from(outGoingNetexExport)
                .log(LoggingLevel.INFO, "Incoming message from kingu export")
                //.filter(e -> e.getIn().getHeader(Constants.EXPORT_JOB_NAME) == null)
                .log(LoggingLevel.INFO, "Done processing Tiamat exports: ${body}")
                .log(LoggingLevel.INFO, "Export location is $simple{in.header.exportLocation}")
                .setHeader(Constants.FILE_HANDLE, simple(blobStoreSourceSubdirectoryForTiamatExport + "/${in.header.exportLocation}"))
                .choice()
                .when(header(Constants.EXPORT_JOB_NAME).isEqualTo("tiamat_export_geocoder"))
                .setHeader(Constants.TARGET_FILE_HANDLE, simple(blobStoreSubdirectoryForTiamatGeoCoderExport + "/tiamat_export_geocoder_latest.zip"))
                .to("direct:copyGeoCoderBlob")
                .otherwise()
                .setHeader(Constants.TARGET_FILE_HANDLE, simple(blobStoreSubdirectoryForTiamatExport + "/${in.header.exportJobName}_latest.zip"))
                .to("direct:kinguExportUploadFileExternal")
                .end()
                .routeId("from-tiamat-export-queue-processed");

        from("direct:kinguExportUploadFileExternal")
                .log(LoggingLevel.INFO, "kingu export upload external")
                .setHeader(BLOBSTORE_MAKE_BLOB_PUBLIC, constant(true))
                .to("direct:copyKinguBlob")
                .routeId("kingu-export-upload-file-external");
    }
}
