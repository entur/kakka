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

package no.entur.kakka.repository;

import com.google.cloud.storage.Storage;
import no.entur.kakka.domain.BlobStoreFiles;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.output.ByteArrayOutputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Repository
@Profile("in-memory-blobstore")
public class InMemoryBlobStoreRepository implements BlobStoreRepository {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    private final Map<String, byte[]> blobs = new HashMap<>();

    @Override
    public BlobStoreFiles listBlobs(String prefix) {
        return listBlobs(Collections.singletonList(prefix));
    }

    @Override
    public BlobStoreFiles listBlobs(Collection<String> prefixes) {
        logger.debug("list blobs called in in-memory blob store");
        List<BlobStoreFiles.File> files = blobs.keySet().stream()
                .filter(k -> prefixes.stream().anyMatch(k::startsWith))
                .map(k -> new BlobStoreFiles.File(k, new Date(), new Date(), 1234L))    //TODO Add real details?
                .toList();
        BlobStoreFiles blobStoreFiles = new BlobStoreFiles();
        blobStoreFiles.add(files);
        return blobStoreFiles;
    }

    @Override
    public BlobStoreFiles listBlobsFlat(String prefix) {
        List<BlobStoreFiles.File> files = listBlobs(prefix).getFiles();
        List<BlobStoreFiles.File> result = files.stream().map(k -> new BlobStoreFiles.File(k.getName().replaceFirst(prefix + "/", ""), new Date(), new Date(), 1234L)).collect(Collectors.toList());
        BlobStoreFiles blobStoreFiles = new BlobStoreFiles();
        blobStoreFiles.add(result);
        return blobStoreFiles;
    }

    @Override
    public InputStream getBlob(String objectName) {
        logger.debug("get blob called in in-memory blob store");
        byte[] data = blobs.get(objectName);
        return (data == null) ? null : new ByteArrayInputStream(data);
    }

    @Override
    public void uploadBlob(String objectName, InputStream inputStream, boolean makePublic, String contentType) {
        uploadBlob(objectName, inputStream, makePublic);
    }

    @Override
    public void uploadBlob(String objectName, InputStream inputStream, boolean makePublic) {
        try {
            logger.debug("upload blob called in in-memory blob store");
            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            IOUtils.copy(inputStream, byteArrayOutputStream);
            byte[] data = byteArrayOutputStream.toByteArray();
            blobs.put(objectName, data);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void copyBlob(String sourceObjectName, String targetObjectName, boolean makePublic) {
        byte[] sourceData = blobs.get(sourceObjectName);
        blobs.put(targetObjectName, sourceData);
    }

    @Override
    public void copyKinguBlob(String sourceObjectName, String targetObjectName, boolean makePublic) {
        copyBlob(sourceObjectName, targetObjectName, makePublic);
    }

    @Override
    public void copyGeoCoderBlob(String sourceObjectName, String targetObjectName, boolean makePublic) {
        copyBlob(sourceObjectName, targetObjectName, makePublic);
    }

    @Override
    public boolean delete(String objectName) {
        blobs.remove(objectName);
        return true;
    }

    @Override
    public void setStorage(Storage storage) {

    }

    @Override
    public void setTargetStorage(Storage targetStorage) {

    }

    @Override
    public void setContainerName(String containerName) {

    }

    @Override
    public void setKinguContainerName(String kinguContainerName) {

    }

    @Override
    public void setTargetContainerName(String targetContainerName) {

    }

    @Override
    public boolean deleteAllFilesInFolder(String folder) {
        listBlobs(folder).getFiles().forEach(file -> delete(file.getName()));
        return true;
    }
}
