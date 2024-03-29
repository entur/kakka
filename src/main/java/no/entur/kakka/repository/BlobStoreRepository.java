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

import java.io.InputStream;
import java.util.Collection;

public interface BlobStoreRepository {

    BlobStoreFiles listBlobs(Collection<String> prefixes);

    BlobStoreFiles listBlobs(String prefix);

    BlobStoreFiles listBlobsFlat(String prefix);

    InputStream getBlob(String objectName);

    void uploadBlob(String objectName, InputStream inputStream, boolean makePublic);

    void uploadBlob(String objectName, InputStream inputStream, boolean makePublic, String contentType);

    void copyBlob(String sourceObjectName, String targetObjectName, boolean makePublic);

    void copyKinguBlob(String sourceObjectName, String targetObjectName, boolean makePublic);

    void copyGeoCoderBlob(String sourceObjectName, String targetObjectName, boolean makePublic);

    void setStorage(Storage storage);

    void setTargetStorage(Storage targetStorage);

    void setContainerName(String containerName);

    void setKinguContainerName(String kinguContainerName);

    void setTargetContainerName(String targetContainerName);

    boolean delete(String objectName);

    boolean deleteAllFilesInFolder(String folder);

}
