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

import com.google.cloud.storage.Acl;
import com.google.cloud.storage.Blob;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.Storage;
import no.entur.kakka.domain.BlobStoreFiles;
import org.apache.commons.lang3.StringUtils;
import org.rutebanken.helper.gcp.BlobStoreHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Repository;

import java.io.InputStream;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.Iterator;

@Repository
@Profile("gcs-blobstore")
@Scope("prototype")
public class GcsBlobStoreRepository implements BlobStoreRepository {

    private Logger logger = LoggerFactory.getLogger(this.getClass());

    private Storage storage;

    private String containerName;


    @Override
    public void setStorage(Storage storage) {
        this.storage = storage;
    }

    @Override
    public void setContainerName(String containerName) {
        this.containerName = containerName;
    }

    @Override
    public BlobStoreFiles listBlobs(Collection<String> prefixes) {
        BlobStoreFiles blobStoreFiles = new BlobStoreFiles();


        for (String prefix : prefixes) {
            Iterator<Blob> blobIterator = BlobStoreHelper.listAllBlobsRecursively(storage, containerName, prefix);
            blobIterator.forEachRemaining(blob -> blobStoreFiles.add(toBlobStoreFile(blob, blob.getName())));
        }

        return blobStoreFiles;
    }

    @Override
    public BlobStoreFiles listBlobs(String prefix) {
        return listBlobs(Arrays.asList(prefix));
    }


    @Override
    public BlobStoreFiles listBlobsFlat(String prefix) {
        Iterator<Blob> blobIterator = BlobStoreHelper.listAllBlobsRecursively(storage, containerName, prefix);
        BlobStoreFiles blobStoreFiles = new BlobStoreFiles();
        while (blobIterator.hasNext()) {
            Blob blob = blobIterator.next();
            String fileName = blob.getName().replace(prefix, "");
            if (!StringUtils.isEmpty(fileName)) {
                blobStoreFiles.add(toBlobStoreFile(blob, fileName));
            }
        }

        return blobStoreFiles;
    }

    @Override
    public InputStream getBlob(String name) {
        return BlobStoreHelper.getBlob(storage, containerName, name);
    }

    @Override
    public void uploadBlob(String name, byte[] content, boolean makePublic) {
        BlobStoreHelper.uploadBlob(storage, containerName, name, content, makePublic);
    }


    @Override
    public boolean delete(String objectName) {
        return BlobStoreHelper.delete(storage, BlobId.of(containerName, objectName));
    }



    private BlobStoreFiles.File toBlobStoreFile(Blob blob, String fileName) {
        BlobStoreFiles.File file = new BlobStoreFiles.File(fileName, new Date(blob.getCreateTime()), new Date(blob.getUpdateTime()), blob.getSize());

        if (blob.getAcl() != null) {
            if (blob.getAcl().stream().anyMatch(acl -> Acl.User.ofAllUsers().equals(acl.getEntity()) && acl.getRole() != null)) {
                file.setUrl(blob.getMediaLink());
            }
        }
        return file;
    }


}
