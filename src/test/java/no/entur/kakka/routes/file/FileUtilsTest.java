package no.entur.kakka.routes.file;

import no.entur.kakka.exceptions.KakkaException;
import org.apache.camel.util.FileUtil;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class FileUtilsTest {


    @Test
    public void testRenameFilesInZip() throws Exception {
        String replaceText = "YYYYYY";

        String folder = "target/rename-test";
        FileUtil.removeDir(new File(folder));
        Files.createDirectory(Paths.get(folder));
        new File(folder + "/stops.txt").createNewFile();
        new File(folder + "/stops-22.txt").createNewFile();
        new File(folder + "/other.txt").createNewFile();
        KakkaFileUtils.renameFiles(folder, "stops", replaceText);

        Assertions.assertFalse(new File(folder + "/stops.txt").exists());
        Assertions.assertFalse(new File(folder + "/stops-22.txt").exists());
        Assertions.assertTrue(new File(folder + "/" + replaceText + ".txt").exists());
        Assertions.assertTrue(new File(folder + "/" + replaceText + "-22.txt").exists());
        Assertions.assertTrue(new File(folder + "/other.txt").exists());
    }

    @Test
    public void testRenameFilesInZipFolderDoesNotExist() {
        Assertions.assertThrows(KakkaException.class, () -> {
            String replaceText = "YYYYYY";
            String folder = "target/rename-test-not-existing";
            KakkaFileUtils.renameFiles(folder, "stops", replaceText);
        });
    }

    @Test
    public void testRenameFilesInZipFolderNotADirectory() throws IOException {
        Assertions.assertThrows(KakkaException.class, () -> {
            String replaceText = "YYYYYY";
            Path path = Files.createTempFile("rename-test-not-a-folder", "");
            KakkaFileUtils.renameFiles(path.toString(), "stops", replaceText);
        });
    }

}
