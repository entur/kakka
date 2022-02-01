package no.entur.kakka.exceptions;

public class KakkaZipFileEntryContentEncodingException extends FileValidationException {
    public KakkaZipFileEntryContentEncodingException(Throwable cause) {
        super(cause);
    }

    public KakkaZipFileEntryContentEncodingException(String message, Throwable cause) {
        super(message, cause);
    }
}
