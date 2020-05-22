package no.entur.kakka.routes.file;

import no.entur.kakka.exceptions.KakkaException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

public class FileUtils {

    private static final Logger LOGGER = LoggerFactory.getLogger(FileUtils.class);

    public static void renameFiles(String folder, String target, String replacement) {

        File containingFolder = new File(folder);
        if (!containingFolder.exists()) {
            throw new KakkaException("Cannot rename files: The folder " + folder + " does not exist");
        }
        if (!containingFolder.isDirectory()) {
            throw new KakkaException("Cannot rename files: The folder " + folder + " is not a directory");
        }
        String[] filesInFolder = containingFolder.list();
        if (filesInFolder.length == 0) {
            LOGGER.warn("No files renamed: The folder {} is empty", folder);
        }
        try {
            for (String fileName : filesInFolder) {
                if (fileName.contains(target)) {

                    Files.move(Paths.get(folder, fileName), Paths.get(folder, fileName.replace(target, replacement)));
                }
            }
        } catch (IOException ioe) {
            throw new KakkaException("Failed to rename files in folder " + folder + " : " + ioe.getMessage(), ioe);
        }
    }
}
