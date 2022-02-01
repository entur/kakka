package no.entur.kakka.routes.file.beans;

import no.entur.kakka.routes.file.FileType;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static no.entur.kakka.routes.file.FileType.INVALID_FILE_NAME;
import static no.entur.kakka.routes.file.FileType.INVALID_ZIP_FILE_ENTRY_NAME_ENCODING;
import static no.entur.kakka.routes.file.FileType.NETEXPROFILE;
import static no.entur.kakka.routes.file.FileType.NOT_A_ZIP_FILE;
import static no.entur.kakka.routes.file.FileType.UNKNOWN_FILE_TYPE;
import static no.entur.kakka.routes.file.FileType.ZIP_CONTAINS_MORE_THAN_ONE_FILE;
import static no.entur.kakka.routes.file.FileType.ZIP_CONTAINS_SUBDIRECTORIES;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FileTypeClassifierBeanTest {
    private FileTypeClassifierBean bean;

    @BeforeEach
    void before() {
        bean = new FileTypeClassifierBean();
    }


    @Test
    void classifyNetexFile() throws Exception {
        assertFileType("tariff_zones.zip", NETEXPROFILE);
    }

    @Test
    void classifyNetexFileFromRuter() throws IOException {
        assertFileType("invalid_zip.zip", ZIP_CONTAINS_MORE_THAN_ONE_FILE);
    }

    @Test
    void classifyNetexWithNeptuneFileNameInside() throws Exception {
        assertFileType("tariff_zones.zip", NETEXPROFILE);
    }

    @Test
    void classifyNetexWithTwoFiles() throws Exception {
        assertFileType("tariff_zones_fare_zones_rut.zip", ZIP_CONTAINS_MORE_THAN_ONE_FILE);
    }


    @Test
    void classifyNotAZipFile() throws IOException {
        assertFileType("not_a_zip_file.zip", NOT_A_ZIP_FILE);
    }

    @Test
    void classifyZipFilContainsSubdirectories() throws IOException {
        assertFileType("tariff_zones_sub_dir.zip", ZIP_CONTAINS_SUBDIRECTORIES);
    }

    @Test
    void classifyEmptyZipFile() throws IOException {
        assertFileType("empty_zip.zip", NOT_A_ZIP_FILE);
    }

    @Test
    void classifyInvalidEncodingInZipEntryName() throws IOException {
        assertFileType("zip_file_with_invalid_encoding_in_entry_name.zip", INVALID_ZIP_FILE_ENTRY_NAME_ENCODING);
    }

    @Test
    void classifyUnknownFileType() throws IOException {
        assertFileType("unknown_file_type.zip", UNKNOWN_FILE_TYPE);
    }

    @Test
    void classifyFileNameWithNonISO_8859_1CharacterAsInvalid() throws Exception {
        // The å in ekspressbåt below is encoded as 97 ('a') + 778 (ring above)
        byte[] data = IOUtils.toByteArray(this.getClass().getResourceAsStream("tariff_zones.zip"));
        assertFileType("sof-20170904121616-2907_20170904_Buss_og_ekspressbåt_til_rutesøk_19.06.2017-28.02.2018 (1).zip", data, INVALID_FILE_NAME);
    }

    @Test
    void classifyFileNameWithOnlyISO_8859_1CharacterAsValid() throws Exception {
        // The å in ekspressbåt below is encoded as a regular 229 ('å')
        byte[] data = IOUtils.toByteArray(this.getClass().getResourceAsStream("tariff_zones.zip"));
        assertFileType("sof-20170904121616-2907_20170904_Buss_og_ekspressbåt_til_rutesøk_19.06.2017-28.02.2018 (1).zip", data, NETEXPROFILE);
    }

    @Test
    void xMLFilePatternShouldMatchXMLFiles() {
        assertTrue(FileTypeClassifierBean.XML_FILES_REGEX.matcher("test.xml").matches());
        assertTrue(FileTypeClassifierBean.XML_FILES_REGEX.matcher("test.test.xml").matches());
    }

    @Test
    void xMLFilePatternShouldNotMatchOtherFileTypes() {
        assertFalse(FileTypeClassifierBean.XML_FILES_REGEX.matcher("test.log").matches());
        assertFalse(FileTypeClassifierBean.XML_FILES_REGEX.matcher("test.xml.log").matches());
        assertFalse(FileTypeClassifierBean.XML_FILES_REGEX.matcher("test.xml2").matches());
        assertFalse(FileTypeClassifierBean.XML_FILES_REGEX.matcher("test.txml").matches());
    }

    @Test
    void xMLFilePatternShouldMatchOnlyLowerCaseExtension() {
        assertFalse(FileTypeClassifierBean.XML_FILES_REGEX.matcher("test.XML").matches());
        assertFalse(FileTypeClassifierBean.XML_FILES_REGEX.matcher("test.Xml").matches());
    }

    private void assertFileType(String fileName, FileType expectedFileType) throws IOException {
        byte[] data = IOUtils.toByteArray(this.getClass().getResourceAsStream(fileName));
        assertFileType(fileName, data, expectedFileType);
    }


    private void assertFileType(String fileName, byte[] data, FileType expectedFileType) {
        FileType resultType = bean.classifyFile(fileName, data);
        assertEquals(expectedFileType, resultType);
    }
}