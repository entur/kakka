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

package no.entur.kakka.geocoder.routes.kartverket;

import no.entur.kakka.Constants;
import no.entur.kakka.domain.BlobStoreFiles;
import no.entur.kakka.exceptions.KakkaException;
import no.entur.kakka.geocoder.BaseRouteBuilder;
import no.entur.kakka.geocoder.routes.util.MarkContentChangedAggregationStrategy;
import no.entur.kakka.services.BlobStoreService;
import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Paths;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.apache.camel.Exchange.FILE_PARENT;

@Component
public class KartverketFileRouteBuilder extends BaseRouteBuilder {

    @Value("${kartverket.download.directory:files/kartverket}")
    private String localDownloadDir;

    @Autowired
    private BlobStoreService blobStoreService;

    @Override
    public void configure() throws Exception {
        super.configure();

        from("direct:uploadUpdatedFiles")
                .setHeader(FILE_PARENT, simple(localDownloadDir + "/${date:now:yyyyMMddHHmmss}"))
                .doTry()
                .bean("kartverketService", "downloadFiles")
                .process(this::deleteNoLongerActiveFiles)
                .to("direct:kartverketUploadOnlyUpdatedFiles")
                .doFinally()
                .to("direct:cleanUpLocalDirectory")
                .routeId("upload-updated-files");

        from("direct:kartverketUploadOnlyUpdatedFiles")
                .split().body().aggregationStrategy(new MarkContentChangedAggregationStrategy())
                .to("direct:kartverketUploadFileIfUpdated")
                .routeId("kartverket-upload-only--updated-files");


        from("direct:kartverketUploadFileIfUpdated")
                .setHeader(Exchange.FILE_NAME, simple("${body.name}"))
                .setHeader(Constants.FILE_HANDLE, simple("${header." + Constants.FOLDER_NAME + "}/${body.name}"))
                .filter(this::contentChanged)
                .log(LoggingLevel.INFO, "Uploading ${header." + Constants.FILE_HANDLE + "}")
                .to("direct:uploadBlob")
                .setHeader(Constants.CONTENT_CHANGED, constant(true))
                .end()
                .routeId("upload-file-if-updated");
    }

    /**
     * Detect whether a downloaded file differs from the version already stored in the blob store, by
     * comparing MD5 digests. The previously uploaded blob (at the same file handle) acts as the
     * reference, so no separate state store is required. A missing reference blob is treated as changed.
     */
    private boolean contentChanged(Exchange e) {
        File file = e.getIn().getBody(File.class);
        String fileHandle = e.getIn().getHeader(Constants.FILE_HANDLE, String.class);
        String newDigest = digestOf(file, fileHandle);
        String existingDigest = existingBlobDigest(fileHandle, e);
        return !newDigest.equals(existingDigest);
    }

    private String digestOf(File file, String fileHandle) {
        try (InputStream in = new FileInputStream(file)) {
            return DigestUtils.md5Hex(in);
        } catch (IOException ex) {
            throw new KakkaException("Failed to compute digest for " + fileHandle, ex);
        }
    }

    private String existingBlobDigest(String fileHandle, Exchange e) {
        try (InputStream existing = blobStoreService.getBlob(fileHandle, e)) {
            return existing != null ? DigestUtils.md5Hex(existing) : null;
        } catch (IOException ex) {
            log.warn("Failed to read existing blob {} for change detection; treating as changed", fileHandle, ex);
            return null;
        }
    }

    private void deleteNoLongerActiveFiles(Exchange e) {
        List<File> activeFiles = e.getIn().getBody(List.class);
        Set<String> activeFileNames = activeFiles.stream().map(File::getName).collect(Collectors.toSet());
        BlobStoreFiles blobs = blobStoreService.listBlobsInFolder(e.getIn().getHeader(Constants.FOLDER_NAME, String.class), e);

        blobs.getFiles().stream().filter(b -> !activeFileNames.contains(Paths.get(b.getName()).getFileName().toString())).forEach(b -> deleteNoLongerActiveBlob(b, e));

    }

    private void deleteNoLongerActiveBlob(BlobStoreFiles.File blob, Exchange e) {
        log.info("Delete blob no longer part of Kartverekt dataset: {}", blob);
        blobStoreService.deleteBlob(blob.getName(), e);
    }
}
