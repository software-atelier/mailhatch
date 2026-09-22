package ch.softwareatelier.mailhatch;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Deliberately rejects an SMTP command or completed message with a caller-selected reply.
 *
 * <p>Only 4xx and 5xx replies are allowed. Response text is validated to prevent SMTP
 * response injection.</p>
 */
public final class SmtpReplyException extends Exception {
    private static final Pattern ENHANCED_STATUS = Pattern.compile("[45]\\.[0-9]{1,3}\\.[0-9]{1,3}");

    private final int replyCode;
    private final String enhancedStatus;

    /**
     * Creates an SMTP rejection.
     *
     * @param replyCode SMTP reply code from 400 through 599
     * @param enhancedStatus enhanced status such as {@code 5.1.1}; its class must match the reply code
     * @param message human-readable single-line response text
     */
    public SmtpReplyException(int replyCode, String enhancedStatus, String message) {
        super(validateMessage(message));
        if (replyCode < 400 || replyCode > 599) {
            throw new IllegalArgumentException("replyCode must be between 400 and 599");
        }
        this.enhancedStatus = Objects.requireNonNull(enhancedStatus, "enhancedStatus");
        if (!ENHANCED_STATUS.matcher(enhancedStatus).matches()
                || enhancedStatus.charAt(0) != Integer.toString(replyCode).charAt(0)) {
            throw new IllegalArgumentException("enhancedStatus must be a valid 4.x.x or 5.x.x code matching replyCode");
        }
        this.replyCode = replyCode;
    }

    /** @return SMTP reply code */
    public int replyCode() {
        return replyCode;
    }

    /** @return enhanced SMTP status code */
    public String enhancedStatus() {
        return enhancedStatus;
    }

    /** @return complete single-line SMTP response without CRLF */
    public String responseLine() {
        return replyCode + " " + enhancedStatus + " " + getMessage();
    }

    private static String validateMessage(String message) {
        Objects.requireNonNull(message, "message");
        if (message.isBlank()) throw new IllegalArgumentException("message must not be blank");
        if (message.indexOf('\r') >= 0 || message.indexOf('\n') >= 0) {
            throw new IllegalArgumentException("message must be a single line");
        }
        return message;
    }
}
