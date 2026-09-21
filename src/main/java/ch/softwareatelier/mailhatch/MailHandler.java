package ch.softwareatelier.mailhatch;

/** Receives a fully parsed message after the SMTP transaction has completed. */
@FunctionalInterface
public interface MailHandler {
    /**
     * Handles one accepted SMTP message.
     *
     * @param message parsed message and SMTP envelope
     * @throws Exception to reject the SMTP transaction with a temporary failure
     */
    void handle(InboundMessage message) throws Exception;
}
