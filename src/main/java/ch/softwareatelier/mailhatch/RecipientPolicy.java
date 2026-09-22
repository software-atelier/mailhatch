package ch.softwareatelier.mailhatch;

/** Evaluates SMTP envelope recipients before message data is accepted. */
@FunctionalInterface
public interface RecipientPolicy {
    /**
     * Accepts or rejects one proposed recipient.
     *
     * <p>Return normally to accept the recipient. Throw {@link SmtpReplyException} to
     * reject it with a deliberate temporary or permanent SMTP reply. Any other exception
     * is treated as a temporary policy failure. Implementations run on Netty's I/O thread
     * and must not perform blocking I/O; use an in-memory cache for remote policy data.</p>
     *
     * @param context current envelope state and proposed recipient
     * @throws Exception if the recipient is rejected or policy evaluation fails
     */
    void validate(RecipientContext context) throws Exception;

    /** @return a policy that accepts every syntactically valid recipient */
    static RecipientPolicy acceptAll() {
        return ignored -> { };
    }
}
