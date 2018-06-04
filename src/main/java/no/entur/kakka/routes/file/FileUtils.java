package no.entur.kakka.routes.file;

import no.entur.kakka.exceptions.KakkaException;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

public class FileUtils {

    public static void renameFiles(String folder, String target, String replacement) {
        try {
            for (String fileName : new File(folder).list()) {
                if (fileName.contains(target)) {

                    Files.move(Paths.get(folder, fileName), Paths.get(folder, fileName.replace(target, replacement)));
                }
            }
        } catch (IOException ioe) {
            throw new KakkaException("Failed to zip files in folder: " + ioe.getMessage(), ioe);
        }
    }
}
