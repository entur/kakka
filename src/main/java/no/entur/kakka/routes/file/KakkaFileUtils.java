package no.entur.kakka.routes.file;

import no.entur.kakka.exceptions.KakkaException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

public class KakkaFileUtils {

    private static final Logger LOGGER = LoggerFactory.getLogger(KakkaFileUtils.class);

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

    /**
     * Return true if the fileName does not contain new lines, tabs and any non-ISO_8859_1 characters.
     *
     * @param fileName the file name to test.
     * @return true if the fileName does not contain  new lines, tab and any non-ISO_8859_1 characters
     */
    public static boolean isValidFileName(String fileName) {
        return !fileName.contains("\n")
                && !fileName.contains("\r")
                && !fileName.contains("\t")
                && StandardCharsets.ISO_8859_1.newEncoder().canEncode(fileName);
    }

    public static File createTempFile(byte[] data, String prefix, String suffix) throws IOException {
        File inputFile = File.createTempFile(prefix, suffix);
        try (FileOutputStream fos = new FileOutputStream(inputFile)) {
            fos.write(data);
        }
        return inputFile;
    }


    /**
     * Remove new lines, tabs and any non-ISO_8859_1 characters from file name.
     * New lines and tabs may cause security issues.
     * Non-ISO_8859_1 characters cause chouette import to crash.
     *
     * @param fileName the file name to sanitize.
     * @return a file name where new lines, tab and any non ISO_8859_1 characters are filtered out.
     */
    public static String sanitizeFileName(String fileName) {

        StringBuilder result = new StringBuilder(fileName.length());
        CharsetEncoder charsetEncoder = StandardCharsets.ISO_8859_1.newEncoder();
        for (char val : fileName.toCharArray()) {

            if (val == '\n' || val == '\r' || val == '\t') {
                continue;
            }

            if (charsetEncoder.canEncode(val)) {
                result.append(val);
            }
        }
        return result.toString();
    }
}
