package ch.softwareatelier.mailhatch;

/** Receives a fully parsed message after the SMTP transaction has completed. */
@FunctionalInterface
public interface MailHandler {
    /**
     * Handles one accepted SMTP message.
     *
     * @param message parsed message and SMTP envelope
     * @throws SmtpReplyException to reject with a deliberate 4xx or 5xx SMTP reply
     * @throws Exception to reject the SMTP transaction with a generic temporary failure
     */
    void handle(InboundMessage message) throws Exception;
}
