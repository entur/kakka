package no.entur.kakka.routes.file;

import no.entur.kakka.exceptions.KakkaException;
import org.apache.camel.util.FileUtil;
import org.junit.Assert;
import org.junit.Test;

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
        FileUtils.renameFiles(folder, "stops", replaceText);

        Assert.assertFalse(new File(folder + "/stops.txt").exists());
        Assert.assertFalse(new File(folder + "/stops-22.txt").exists());
        Assert.assertTrue(new File(folder + "/" + replaceText + ".txt").exists());
        Assert.assertTrue(new File(folder + "/" + replaceText + "-22.txt").exists());
        Assert.assertTrue(new File(folder + "/other.txt").exists());
    }

    @Test(expected = KakkaException.class)
    public void testRenameFilesInZipFolderDoesNotExist() {
        String replaceText = "YYYYYY";
        String folder = "target/rename-test-not-existing";
        FileUtils.renameFiles(folder, "stops", replaceText);
    }

    @Test(expected = KakkaException.class)
    public void testRenameFilesInZipFolderNotADirectory() throws IOException {
        String replaceText = "YYYYYY";
        Path path = Files.createTempFile("rename-test-not-a-folder", "");
        FileUtils.renameFiles(path.toString(), "stops", replaceText);
    }

}
