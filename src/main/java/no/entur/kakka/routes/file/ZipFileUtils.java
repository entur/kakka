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

import no.entur.kakka.exceptions.KakkaException;
import no.entur.kakka.exceptions.KakkaZipFileEntryNameEncodingException;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.MalformedInputException;
import java.nio.file.Files;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public class ZipFileUtils {
    private static final Logger logger = LoggerFactory.getLogger(ZipFileUtils.class);

    public static void unzipFile(InputStream inputStream, String targetFolder) {
        try {
            byte[] buffer = new byte[1024];
            ZipInputStream zis = new ZipInputStream(inputStream);
            ZipEntry zipEntry = zis.getNextEntry();
            while (zipEntry != null) {
                String fileName = zipEntry.getName();
                logger.info("unzipping file {} in folder {} ", fileName, targetFolder);

                File newFile = new File(targetFolder + "/" + fileName);
                if (fileName.endsWith("/")) {
                    newFile.mkdirs();
                    continue;
                }

                File parent = newFile.getParentFile();
                if (parent != null) {
                    parent.mkdirs();
                }


                FileOutputStream fos = new FileOutputStream(newFile);
                int len;
                while ((len = zis.read(buffer)) > 0) {
                    fos.write(buffer, 0, len);
                }
                fos.close();
                zipEntry = zis.getNextEntry();
            }
            zis.closeEntry();
            zis.close();
        } catch (IOException ioE) {
            throw new RuntimeException("Unzipping archive failed: " + ioE.getMessage(), ioE);
        }
    }


    public static void unzipAddressFile(InputStream inputStream, String targetFolder) {
        try {
            byte[] buffer = new byte[1024];
            ZipInputStream zis = new ZipInputStream(inputStream);
            ZipEntry zipEntry = zis.getNextEntry();
            while (zipEntry != null) {
                String fileName = zipEntry.getName();

                File newFile = new File(targetFolder + File.separator + fileName);
                logger.info("unzipping file: {}", newFile.getAbsoluteFile());

                //create all non exists folders
                //else you will hit FileNotFoundException for compressed folder
                new File(newFile.getParent()).mkdirs();

                if (!zipEntry.isDirectory()) {
                    var fileOutputStream = new FileOutputStream(newFile);

                    int len;
                    while ((len = zis.read(buffer)) > 0) {
                        fileOutputStream.write(buffer, 0, len);
                    }
                    fileOutputStream.close();
                }
                zipEntry = zis.getNextEntry();
            }
            zis.closeEntry();
            zis.close();
        } catch (IOException ioE) {
            throw new RuntimeException("Unzipping archive failed: " + ioE.getMessage(), ioE);
        }
    }

    public static File zipFilesInFolder(String folder, String targetFilePath) {
        try {

            FileOutputStream out = new FileOutputStream(new File(targetFilePath));
            ZipOutputStream outZip = new ZipOutputStream(out);

            FileUtils.listFiles(new File(folder), null, false).stream().forEach(file -> addToZipFile(file, outZip));

            outZip.close();
            out.close();

            return new File(targetFilePath);
        } catch (IOException ioe) {
            throw new KakkaException("Failed to zip files in folder: " + ioe.getMessage(), ioe);
        }
    }


    public static void addToZipFile(File file, ZipOutputStream zos) {
        try {
            FileInputStream fis = new FileInputStream(file);
            ZipEntry zipEntry = new ZipEntry(file.getName());
            zos.putNextEntry(zipEntry);

            byte[] bytes = new byte[1024];
            int length;
            while ((length = fis.read(bytes)) >= 0) {
                zos.write(bytes, 0, length);
            }

            zos.closeEntry();
            fis.close();
        } catch (IOException ioe) {
            throw new KakkaException("Failed to add file to zip: " + ioe.getMessage(), ioe);
        }
    }

    /**
     * Test if the given byte arrray contains a zip file.
     * The test is performed by matching the magic number at the beginning of the array with the zip file magic number
     * (PK\x03\x04). Magic numbers for empty archives (PK\x05\x06) or spanned archives (PK\x07\x08) are rejected.
     *
     * @param data
     * @return
     */
    public static boolean isZipFile(byte[] data) {
        if (data == null || data.length < 4) {
            return false;
        }
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(data))) {
            return in.readInt() == 0x504b0304;
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * List the entries in the zip file.
     * The byte array is first saved to disk to avoid using a ZipInputStream that would parse the whole stream to
     * find entries.
     * @param data a byte array containing a zip archive.
     * @return the list of entries in the zip archive.
     * @throws IOException
     * @throws KakkaZipFileEntryNameEncodingException if an entry is not UTF8-encoded.
     */
    public static Set<ZipEntry> listFilesInZip(byte[] data) throws IOException {
        File tmpFile = KakkaFileUtils.createTempFile(data, "kakka-list-files-in-zip-", ".zip");
        Set<ZipEntry> fileList = listFilesInZip(tmpFile);
        Files.delete(tmpFile.toPath());
        return fileList;
    }

    /**
     * List the entries in the zip file.
     * The byte array is first saved to disk to avoid using a ZipInputStream that would parse the whole stream to
     * find entries.
     * @param file the zip archive.
     * @return the list of entries in the zip archive.
     * @throws IOException
     * @throws KakkaZipFileEntryNameEncodingException if an entry is not UTF8-encoded.
     */
    public static Set<ZipEntry> listFilesInZip(File file) {
        try (ZipFile zipFile = new ZipFile(file)) {
            return zipFile.stream().collect(Collectors.toSet());
        } catch (IllegalArgumentException e) {
            Throwable rootCause = ExceptionUtils.getRootCause(e);
            if (rootCause instanceof MalformedInputException) {
                throw new KakkaZipFileEntryNameEncodingException(e);
            } else {
                throw new KakkaException(e);
            }
        } catch (ZipException e) {
            if ("invalid CEN header (bad entry name)".equals(e.getMessage())) {
                throw new KakkaZipFileEntryNameEncodingException(e);
            } else {
                throw new KakkaException(e);
            }
        } catch (IOException e) {
            throw new KakkaException(e);
        }
    }
}