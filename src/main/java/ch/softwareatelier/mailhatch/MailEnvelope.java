package ch.softwareatelier.mailhatch;

import java.net.InetSocketAddress;
import java.time.Instant;
import java.util.List;

/**
 * SMTP transport metadata, separate from the message's From/To headers.
 * @param mailFrom SMTP reverse path; empty for the null reverse path
 * @param recipients SMTP forward paths
 * @param helo client EHLO or HELO argument
 * @param remoteAddress client socket address
 * @param receivedAt time at which DATA completed
 * @param tls whether this transaction used TLS
 */
public record MailEnvelope(String mailFrom, List<String> recipients, String helo,
                           InetSocketAddress remoteAddress, Instant receivedAt,
                           boolean tls) {
    public MailEnvelope {
        recipients = List.copyOf(recipients);
    }
}
