package no.entur.kakka.routes.file;

import no.entur.kakka.Constants;
import no.entur.kakka.exceptions.KakkaException;
import no.entur.kakka.geocoder.TransactionalBaseRouteBuilder;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.LoggingLevel;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.input.CloseShieldInputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import org.apache.commons.fileupload.FileItem;
import org.apache.commons.fileupload.FileItemFactory;
import org.apache.commons.fileupload.UploadContext;
import org.apache.commons.fileupload.disk.DiskFileItemFactory;
import org.apache.commons.fileupload.servlet.ServletFileUpload;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

import static no.entur.kakka.Constants.FILE_HANDLE;
import static no.entur.kakka.Constants.FILE_NAME;

@Component
public class FileUploadRouteBuilder extends TransactionalBaseRouteBuilder {

    private static final Logger LOGGER = LoggerFactory.getLogger(FileUploadRouteBuilder.class);

    private static final String FILE_CONTENT_HEADER = "FileContent";

    @Override
    public void configure() throws Exception {
        super.configure();

        // Upload multiple files

        from("direct:uploadFilesAndStartImport")
                .process(this::convertBodyToFileItems)
                .split().body()
                .log(LoggingLevel.INFO,"Upload files and  start Import file name: ${header." + FILE_NAME +  "}")
                .choice()
                .when(header(Constants.FILE_NAME).endsWith("xml"))
                .setHeader(FILE_NAME, simple("TariffZones_${header.providerId}_"+ LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS")) +".xml"))
                .setHeader(FILE_HANDLE, simple("tariffzones/netex/${header.providerId}/${header." + FILE_NAME + "}"))
                .when(header(Constants.FILE_NAME).endsWith("osm"))
                .setHeader(FILE_NAME, simple("TariffZones_${header.providerId}_"+ LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS")) +".osm"))
                .setHeader(FILE_HANDLE, simple("tariffzones/osm/${header.providerId}/${header." + FILE_NAME + "}"))
                .otherwise()
                .log(LoggingLevel.INFO,"Invalid file upload")
                .end()
                .process(e -> e.getIn().setHeader(FILE_CONTENT_HEADER, new CloseShieldInputStream(e.getIn().getBody(FileItem.class).getInputStream())))
                .to("direct:uploadFileAndStartImport")
                .routeId("files-upload");


        // Upload single file
        from("direct:uploadFileAndStartImport").streamCaching()
                .doTry()
                .log(LoggingLevel.INFO,  "Uploading tariff-zone file to blob store: ${header." + FILE_HANDLE + "}")
                .setBody(header(FILE_CONTENT_HEADER))
                .setHeader(Exchange.FILE_NAME, header(FILE_NAME))
                .to("direct:uploadBlob")
                .log(LoggingLevel.INFO,  "Finished uploading tariff-zone file to blob store: ${header." + FILE_HANDLE + "}")
                .setBody(constant(null))
                .to(ExchangePattern.InOnly, "entur-google-pubsub:ProcessTariffZoneFileQueue")
                .log(LoggingLevel.INFO, "Triggered import pipeline for tariff-zone file: ${header." + FILE_HANDLE + "}")
                .doCatch(Exception.class)
                .log(LoggingLevel.WARN, "Upload of tariff-zone data to blob store failed for file: ${header." + FILE_HANDLE + "}")
                .end()
                .routeId("file-upload-and-start-import");

    }

    private void convertBodyToFileItems(Exchange e) {
        FileItemFactory factory = new DiskFileItemFactory();
        ServletFileUpload upload = new ServletFileUpload(factory);

        try {
            byte[] content = IOUtils.toByteArray(e.getIn().getBody(InputStream.class));
            String contentType = e.getIn().getHeader(Exchange.CONTENT_TYPE, String.class);
            LOGGER.debug("Received a multipart request (size: {} bytes) with content type {} ", content.length, contentType);
            SimpleUploadContext uploadContext = new SimpleUploadContext(StandardCharsets.UTF_8, contentType, content);
            List<FileItem> fileItems = upload.parseRequest(uploadContext);

            Optional<String> fileName = fileItems.stream().map(f -> f.getName()).findFirst();
                LOGGER.debug("The multipart request contains {} file(s)", fileItems.size());
                for (FileItem fileItem : fileItems) {
                    LOGGER.debug("Received file {} (size: {})", fileItem.getName(), fileItem.getSize());

                }
            fileName.ifPresent(s -> e.getIn().setHeader(FILE_NAME, s));
            e.getIn().setBody(fileItems);
        } catch (Exception ex) {
            throw new KakkaException("Failed to parse multipart content: " + ex.getMessage());
        }
    }

    public static class SimpleUploadContext implements UploadContext {
        private final Charset charset;
        private final String contentType;
        private final byte[] content;

        public SimpleUploadContext(Charset charset, String contentType, byte[] content) {
            this.charset = charset;
            this.contentType = contentType;
            this.content = content;
        }

        @Override
        public long contentLength() {
            return content.length;
        }

        @Override
        public String getCharacterEncoding() {
            return charset.displayName();
        }

        @Override
        public String getContentType() {
            return contentType;
        }

        @Override
        public int getContentLength() {
            return content.length;
        }

        @Override
        public InputStream getInputStream() throws IOException {
            return new ByteArrayInputStream(content);
        }
    }

}
