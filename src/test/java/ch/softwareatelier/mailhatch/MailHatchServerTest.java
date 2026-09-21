package ch.softwareatelier.mailhatch;

import io.netty.handler.ssl.util.SelfSignedCertificate;
import jakarta.activation.DataHandler;
import jakarta.mail.Message;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import jakarta.mail.util.ByteArrayDataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Properties;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class MailHatchServerTest {
    private static SelfSignedCertificate certificate;

    @BeforeAll
    static void createCertificate() throws Exception {
        certificate = new SelfSignedCertificate("localhost");
    }

    @AfterAll
    static void deleteCertificate() {
        certificate.delete();
    }

    @Test
    void acceptsStartTlsAndParsesMimeContent() throws Exception {
        var received = new LinkedBlockingQueue<InboundMessage>();
        var config = baseConfig()
                .tls(TlsConfig.startTls(certificate.certificate().toPath(), certificate.privateKey().toPath()))
                .build();

        try (var server = new MailHatchServer(config, received::add).start()) {
            send("smtp", server.port(), true, sampleMessage());
            InboundMessage message = received.poll(5, TimeUnit.SECONDS);

            assertThat(message).isNotNull();
            assertThat(message.envelope().tls()).isTrue();
            assertThat(message.envelope().mailFrom()).isEqualTo("sender@example.org");
            assertThat(message.envelope().recipients()).containsExactly("receiver@example.net");
            assertThat(message.subject()).isEqualTo("A useful test message");
            assertThat(message.headers().first("X-MailHatch-Test")).contains("yes");
            assertThat(message.textBody()).contains("plain body");
            assertThat(message.htmlBody()).contains("<strong>HTML body</strong>");
            assertThat(message.attachments()).singleElement().satisfies(attachment -> {
                assertThat(attachment.filename()).isEqualTo("hello.txt");
                assertThat(new String(attachment.content(), StandardCharsets.UTF_8)).isEqualTo("attachment data");
            });
        }
    }

    @Test
    void acceptsImplicitTls() throws Exception {
        var received = new LinkedBlockingQueue<InboundMessage>();
        var config = baseConfig()
                .tls(TlsConfig.implicit(certificate.certificate().toPath(), certificate.privateKey().toPath()))
                .build();

        try (var server = new MailHatchServer(config, received::add).start()) {
            send("smtps", server.port(), false, sampleMessage());
            assertThat(received.poll(5, TimeUnit.SECONDS)).isNotNull();
        }
    }

    @Test
    void acceptsPlainSmtpWhenExplicitlyConfigured() throws Exception {
        var received = new LinkedBlockingQueue<InboundMessage>();
        try (var server = new MailHatchServer(baseConfig().build(), received::add).start()) {
            send("smtp", server.port(), false, sampleMessage());
            assertThat(received.poll(5, TimeUnit.SECONDS)).isNotNull();
        }
    }

    @Test
    void awaitShutdownBlocksUntilServerCloses() throws Exception {
        var server = new MailHatchServer(baseConfig().build(), ignored -> {}).start();
        try (var executor = Executors.newSingleThreadExecutor()) {
            var waiting = executor.submit(() -> {
                server.awaitShutdown();
                return null;
            });

            Thread.sleep(100);
            assertThat(waiting).isNotDone();

            server.close();
            waiting.get(2, TimeUnit.SECONDS);
            assertThat(waiting).isDone();
        } finally {
            server.close();
        }
    }

    private static MailHatchConfig.Builder baseConfig() throws Exception {
        return MailHatchConfig.builder()
                .bindAddress(InetAddress.getLoopbackAddress())
                .port(0)
                .hostname("localhost")
                .idleTimeout(Duration.ofSeconds(30));
    }

    private static MimeMessage sampleMessage() throws Exception {
        var message = new MimeMessage(Session.getInstance(new Properties()));
        message.setFrom(new InternetAddress("sender@example.org", "Sender"));
        message.setRecipient(Message.RecipientType.TO, new InternetAddress("receiver@example.net"));
        message.setSubject("A useful test message", StandardCharsets.UTF_8.name());
        message.setHeader("X-MailHatch-Test", "yes");

        var alternative = new MimeMultipart("alternative");
        var plain = new MimeBodyPart();
        plain.setText("This is the plain body.", StandardCharsets.UTF_8.name());
        alternative.addBodyPart(plain);
        var html = new MimeBodyPart();
        html.setContent("<p>This is the <strong>HTML body</strong>.</p>", "text/html; charset=UTF-8");
        alternative.addBodyPart(html);

        var mixed = new MimeMultipart("mixed");
        var body = new MimeBodyPart();
        body.setContent(alternative);
        mixed.addBodyPart(body);
        var attachment = new MimeBodyPart();
        attachment.setDataHandler(new DataHandler(new ByteArrayDataSource(
                "attachment data".getBytes(StandardCharsets.UTF_8), "text/plain")));
        attachment.setFileName("hello.txt");
        mixed.addBodyPart(attachment);
        message.setContent(mixed);
        message.saveChanges();
        return message;
    }

    private static void send(String protocol, int port, boolean startTls, MimeMessage source) throws Exception {
        var properties = new Properties();
        properties.setProperty("mail.transport.protocol", protocol);
        properties.setProperty("mail." + protocol + ".host", "127.0.0.1");
        properties.setProperty("mail." + protocol + ".port", Integer.toString(port));
        properties.setProperty("mail." + protocol + ".ssl.trust", "*");
        properties.setProperty("mail." + protocol + ".ssl.checkserveridentity", "false");
        if (startTls) {
            properties.setProperty("mail.smtp.starttls.enable", "true");
            properties.setProperty("mail.smtp.starttls.required", "true");
        }

        var session = Session.getInstance(properties);
        var bytes = new ByteArrayOutputStream();
        source.writeTo(bytes);
        var message = new MimeMessage(session, new ByteArrayInputStream(bytes.toByteArray()));
        try (Transport transport = session.getTransport(protocol)) {
            transport.connect();
            transport.sendMessage(message, message.getAllRecipients());
        }
    }
}
