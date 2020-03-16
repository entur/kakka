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

package no.entur.kakka.routes.file;

import org.zeroturnaround.zip.ZipUtil;

import java.io.File;
import java.io.InputStream;

public class ZipFileUtils {

    public static void unzipFile(InputStream inputStream, String targetFolder) {
        ZipUtil.unpack(inputStream, new File(targetFolder));
    }

    public static File zipFilesInFolder(String folder, String targetFilePath) {
        File outputZip = new File(targetFilePath);
        ZipUtil.pack(new File(folder), outputZip);
        return outputZip;
    }

}