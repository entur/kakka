package no.entur.kakka.exceptions;

public class KakkaZipFileEntryContentParsingException extends FileValidationException{

    public KakkaZipFileEntryContentParsingException(Throwable cause) {
        super(cause);
    }

    public KakkaZipFileEntryContentParsingException(String message, Throwable throwable) {
        super(message, throwable);
    }
}
