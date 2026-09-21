package ch.softwareatelier.mailhatch.internal;

import ch.softwareatelier.mailhatch.Attachment;
import ch.softwareatelier.mailhatch.InboundMessage;
import ch.softwareatelier.mailhatch.MailEnvelope;
import ch.softwareatelier.mailhatch.MailHeaders;
import ch.softwareatelier.mailhatch.MessageParseException;
import jakarta.mail.Address;
import jakarta.mail.Header;
import jakarta.mail.Message;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;

public final class MimeMessageParser {
    private static final Session SESSION = Session.getInstance(new Properties());

    public InboundMessage parse(byte[] raw, MailEnvelope envelope) throws MessageParseException {
        try {
            var mime = new MimeMessage(SESSION, new ByteArrayInputStream(raw));
            var headers = new MailHeaders.Builder();
            var allHeaders = mime.getAllHeaders();
            while (allHeaders.hasMoreElements()) {
                Header header = allHeaders.nextElement();
                headers.add(header.getName(), header.getValue());
            }

            var content = new ParsedContent();
            collect(mime, content);
            return new InboundMessage(
                    envelope,
                    headers.build(),
                    mime.getSubject(),
                    mime.getMessageID(),
                    mime.getSentDate() == null ? null : mime.getSentDate().toInstant(),
                    addresses(mime.getFrom()),
                    addresses(mime.getRecipients(Message.RecipientType.TO)),
                    addresses(mime.getRecipients(Message.RecipientType.CC)),
                    content.text,
                    content.html,
                    content.attachments,
                    raw
            );
        } catch (Exception exception) {
            throw new MessageParseException("Unable to parse inbound message", exception);
        }
    }

    private void collect(Part part, ParsedContent result) throws Exception {
        if (part.isMimeType("multipart/*")) {
            Multipart multipart = (Multipart) part.getContent();
            for (int index = 0; index < multipart.getCount(); index++) {
                collect(multipart.getBodyPart(index), result);
            }
            return;
        }

        String disposition = part.getDisposition();
        boolean attachment = Part.ATTACHMENT.equalsIgnoreCase(disposition)
                || part.getFileName() != null
                || (!part.isMimeType("text/plain") && !part.isMimeType("text/html"));
        if (attachment) {
            result.attachments.add(new Attachment(
                    part.getFileName(),
                    part.getContentType(),
                    disposition,
                    firstHeader(part, "Content-ID"),
                    readAll(part.getInputStream())
            ));
            return;
        }

        Object body = part.getContent();
        String value = body instanceof String string ? string : new String(readAll(part.getInputStream()));
        if (part.isMimeType("text/plain") && result.text == null) result.text = value;
        if (part.isMimeType("text/html") && result.html == null) result.html = value;
    }

    private static String firstHeader(Part part, String name) throws Exception {
        String[] values = part.getHeader(name);
        return values == null || values.length == 0 ? null : values[0];
    }

    private static byte[] readAll(InputStream input) throws IOException {
        try (input; var output = new ByteArrayOutputStream()) {
            input.transferTo(output);
            return output.toByteArray();
        }
    }

    private static List<String> addresses(Address[] addresses) {
        if (addresses == null) return List.of();
        var result = new ArrayList<String>(addresses.length);
        for (Address address : addresses) result.add(address.toString());
        return Collections.unmodifiableList(result);
    }

    private static final class ParsedContent {
        private String text;
        private String html;
        private final List<Attachment> attachments = new ArrayList<>();
    }
}
