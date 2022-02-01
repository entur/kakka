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

package no.entur.kakka.routes.file.beans;

import no.entur.kakka.exceptions.FileValidationException;
import no.entur.kakka.exceptions.KakkaException;
import no.entur.kakka.exceptions.KakkaZipFileEntryContentEncodingException;
import no.entur.kakka.exceptions.KakkaZipFileEntryContentParsingException;
import no.entur.kakka.exceptions.KakkaZipFileEntryNameEncodingException;
import no.entur.kakka.routes.file.KakkaFileUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StreamUtils;
import org.springframework.util.xml.StaxUtils;

import javax.xml.namespace.QName;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.CharConversionException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class FileClassifierPredicates {

    public static final QName NETEX_PUBLICATION_DELIVERY_QNAME = new QName("http://www.netex.org.uk/netex", "PublicationDelivery");

    private static final XMLInputFactory xmlInputFactory = StaxUtils.createDefensiveInputFactory();

    private static final Logger LOGGER = LoggerFactory.getLogger(FileClassifierPredicates.class);

    private FileClassifierPredicates() {
    }

    public static Predicate<InputStream> firstElementQNameMatchesNetex() {
        return inputStream -> firstElementQNameMatches(NETEX_PUBLICATION_DELIVERY_QNAME).test(inputStream);
    }

    public static Predicate<InputStream> firstElementQNameMatches(QName qName) {
        return inputStream -> getFirstElementQName(inputStream)
                .orElseThrow(FileValidationException::new)
                .equals(qName);
    }

    private static Optional<QName> getFirstElementQName(InputStream inputStream) {
        XMLStreamReader streamReader = null;
        try {
            streamReader = xmlInputFactory.createXMLStreamReader(inputStream);
            while (streamReader.hasNext()) {
                int eventType = streamReader.next();
                if (eventType == XMLStreamConstants.START_ELEMENT) {
                    return Optional.of(streamReader.getName());
                } else if (eventType != XMLStreamConstants.COMMENT) {
                    // If event is neither start of element or a comment, then this is probably not a xml file.
                    break;
                }
            }
        } catch (XMLStreamException e) {
            Throwable rootCause = ExceptionUtils.getRootCause(e);
            if (rootCause instanceof CharConversionException) {
                throw new KakkaZipFileEntryNameEncodingException(e);
            } else {
                throw new KakkaZipFileEntryContentParsingException(e);
            }
        } finally {
            try {
                if (streamReader != null) {
                    streamReader.close();
                }
            } catch (XMLStreamException e) {
                LOGGER.warn("Exception while closing the stream reader", e);
            }
        }
        return Optional.empty();
    }

    /**
     * Check that files in the zip archive verify the predicate.
     *
     * @param inputStream     the zip file.
     * @param predicate       the predicate to evaluate.
     * @param fileNamePattern the pattern for file names to be tested. Other files are ignored.
     * @return true if all tested files verify the predicate.
     */
    public static boolean validateZipContent(InputStream inputStream, Predicate<InputStream> predicate, Pattern fileNamePattern) {
        try (ZipInputStream stream = new ZipInputStream(inputStream)) {
            ZipEntry entry;
            while ((entry = stream.getNextEntry()) != null) {
                String entryName = entry.getName();
                if (fileNamePattern.matcher(entryName).matches()) {
                    if (testPredicate(predicate, stream, entry)) {
                        return false;
                    }
                } else {
                    if (LOGGER.isInfoEnabled()) {
                        LOGGER.info("Skipped zip entry with name {}", KakkaFileUtils.sanitizeFileName(entryName));
                    }
                }
            }
            return true;
        } catch (IOException e) {
            throw new KakkaException(e);
        }
    }

    private static boolean testPredicate(Predicate<InputStream> predicate, ZipInputStream stream, ZipEntry entry) {
        String entryName = entry.getName();
        try {
            if (!predicate.test(StreamUtils.nonClosing(stream))) {
                if (LOGGER.isInfoEnabled()) {
                    LOGGER.info("Zip entry {} with size {} is invalid.", KakkaFileUtils.sanitizeFileName(entryName), entry.getSize());
                }
                return true;
            }
        } catch (KakkaZipFileEntryContentEncodingException e) {
            throw new KakkaZipFileEntryContentEncodingException("Encoding exception while trying to classify zip file entry " + entryName, e);
        } catch (KakkaZipFileEntryContentParsingException e) {
            throw new KakkaZipFileEntryContentParsingException("Parsing exception while trying to classify zip file entry " + entryName, e);
        } catch (Exception e) {
            throw new KakkaException("Exception while trying to classify zip file entry " + entryName, e);
        }
        return false;
    }
}
