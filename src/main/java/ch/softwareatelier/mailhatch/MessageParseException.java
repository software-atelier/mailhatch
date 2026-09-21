package ch.softwareatelier.mailhatch;

/** Signals a malformed RFC 5322 or MIME message. */
public final class MessageParseException extends Exception {
    /** @param message failure summary @param cause parser failure */
    public MessageParseException(String message, Throwable cause) { super(message, cause); }
}
