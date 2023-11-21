package no.entur.kakka.routes.file;

import no.entur.kakka.geocoder.TransactionalBaseRouteBuilder;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.LoggingLevel;
import org.apache.commons.io.input.CloseShieldInputStream;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.servlet.http.Part;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

import static no.entur.kakka.Constants.*;

@Component
public class FileUploadRouteBuilder extends TransactionalBaseRouteBuilder {

    private static final String FILE_CONTENT_HEADER = "FileContent";

    @Value("${pubsub.kakka.outbound.topic.tariff.zone.file.queue}")
    private String processTariffZoneFileQueueTopic;

    @Override
    public void configure() throws Exception {
        super.configure();

        // Upload multiple files
        from("direct:uploadFilesAndStartImport")
                .split().body()
                .setHeader(FILE_NAME, simple("${body.submittedFileName}"))
                .log(LoggingLevel.INFO, "Upload files and  start Import file name: ${header." + FILE_NAME + "}")
                .choice()
                    .when(header(FILE_NAME).endsWith(XML))
                        .process(this::setHeaders)
                        .to("direct:uploadFileAndStartImport")
                    .otherwise()
                        .log(LoggingLevel.INFO, "Invalid file upload")
                    .end()
                .routeId("upload-files-and-start-import");

        // Upload single file
        from("direct:uploadFileAndStartImport").streamCaching()
                .doTry()
                .log(LoggingLevel.INFO, "Uploading tariff-zone file to blob store: ${header." + FILE_HANDLE + "}")
                .setBody(header(FILE_CONTENT_HEADER))
                .to("direct:uploadBlob")
                .log(LoggingLevel.INFO, "Finished uploading tariff-zone file to blob store: ${header." + FILE_HANDLE + "}")
                .setBody(simple(""))
                .to(ExchangePattern.InOnly, processTariffZoneFileQueueTopic)
                .log(LoggingLevel.INFO, "Triggered import pipeline for tariff-zone file: ${header." + FILE_HANDLE + "}")
                .doCatch(Exception.class)
                .log(LoggingLevel.WARN, "Upload of tariff-zone data to blob store failed for file: ${header." + FILE_HANDLE + "}")
                .end()
                .routeId("upload-file-and-start-import");
    }

    private void setHeaders(Exchange e) throws IOException {
        final String providerId = e.getIn().getHeader("providerId", String.class);
        final String newFileName = "TariffZones_" + providerId + "_" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS")) + ".xml";
        final String newFileHandle = "tariffzones/netex/" + providerId + "/" + newFileName;
        Map<String, Object> headers = new HashMap<>();
        headers.put(FILE_NAME, newFileName);
        headers.put(FILE_HANDLE, newFileHandle);
        headers.put(FILE_CONTENT_HEADER, CloseShieldInputStream.wrap(e.getIn().getBody(Part.class).getInputStream()));
        e.getIn().setHeaders(headers);
    }
}
