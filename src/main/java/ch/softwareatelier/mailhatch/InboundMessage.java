package ch.softwareatelier.mailhatch;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * A handler-friendly representation of an inbound Internet message.
 * @param envelope SMTP transport metadata
 * @param headers all RFC 5322 header fields
 * @param subject decoded Subject, or {@code null}
 * @param messageId Message-ID, or {@code null}
 * @param sentAt Date header as an instant, or {@code null}
 * @param from decoded From header addresses
 * @param to decoded To header addresses
 * @param cc decoded Cc header addresses
 * @param textBody first text/plain body, or {@code null}
 * @param htmlBody first text/html body, or {@code null}
 * @param attachments decoded MIME attachments
 * @param rawMessage original RFC 5322 message bytes
 */
public record InboundMessage(MailEnvelope envelope, MailHeaders headers,
                             String subject, String messageId, Instant sentAt,
                             List<String> from, List<String> to, List<String> cc,
                             String textBody, String htmlBody,
                             List<Attachment> attachments, byte[] rawMessage) {
    public InboundMessage {
        from = List.copyOf(from);
        to = List.copyOf(to);
        cc = List.copyOf(cc);
        attachments = List.copyOf(attachments);
        rawMessage = Arrays.copyOf(rawMessage, rawMessage.length);
    }

    @Override
    public byte[] rawMessage() { return Arrays.copyOf(rawMessage, rawMessage.length); }
    /** @return the Subject when present */
    public Optional<String> optionalSubject() { return Optional.ofNullable(subject); }
    /** @return the Message-ID when present */
    public Optional<String> optionalMessageId() { return Optional.ofNullable(messageId); }
    /** @return the Date header when present and valid */
    public Optional<Instant> optionalSentAt() { return Optional.ofNullable(sentAt); }
    /** @return the first plain-text body when present */
    public Optional<String> optionalTextBody() { return Optional.ofNullable(textBody); }
    /** @return the first HTML body when present */
    public Optional<String> optionalHtmlBody() { return Optional.ofNullable(htmlBody); }
}
