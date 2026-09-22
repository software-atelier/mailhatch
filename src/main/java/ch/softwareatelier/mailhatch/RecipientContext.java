package ch.softwareatelier.mailhatch;

import java.net.InetSocketAddress;
import java.util.List;
import java.util.Objects;

/**
 * SMTP envelope state available while an {@code RCPT TO} command is evaluated.
 *
 * <p>The message body and headers are not available at this stage. The recipient policy
 * runs on the SMTP connection's I/O thread and must therefore return quickly without
 * blocking on network or disk I/O.</p>
 *
 * @param mailFrom SMTP reverse-path; an empty string represents the null reverse-path
 * @param recipient proposed SMTP forward-path
 * @param acceptedRecipients recipients already accepted in this transaction
 * @param helo EHLO or HELO value supplied by the client
 * @param remoteAddress connected client's remote address
 * @param tlsActive whether this connection currently uses TLS
 */
public record RecipientContext(String mailFrom, String recipient,
                               List<String> acceptedRecipients, String helo,
                               InetSocketAddress remoteAddress, boolean tlsActive) {
    public RecipientContext {
        mailFrom = Objects.requireNonNull(mailFrom, "mailFrom");
        recipient = Objects.requireNonNull(recipient, "recipient");
        acceptedRecipients = List.copyOf(acceptedRecipients);
        helo = Objects.requireNonNull(helo, "helo");
        remoteAddress = Objects.requireNonNull(remoteAddress, "remoteAddress");
    }
}
