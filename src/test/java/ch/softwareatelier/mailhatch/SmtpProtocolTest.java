package ch.softwareatelier.mailhatch;

import io.netty.handler.ssl.util.SelfSignedCertificate;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

class SmtpProtocolTest {
    @Test
    void startTlsCanBeRequiredBeforeMailTransaction() throws Exception {
        var certificate = new SelfSignedCertificate("localhost");
        try {
            var config = configBuilder()
                    .tls(TlsConfig.startTls(certificate.certificate().toPath(), certificate.privateKey().toPath()))
                    .build();
            try (var server = new MailHatchServer(config, ignored -> {}).start();
                 var smtp = Client.connect(server.port())) {
                assertThat(smtp.read()).startsWith("220");
                smtp.write("EHLO test.example");
                assertThat(smtp.readEhlo()).contains("250-STARTTLS");
                smtp.write("MAIL FROM:<sender@example.org>");
                assertThat(smtp.read()).startsWith("530");
            }
        } finally {
            certificate.delete();
        }
    }

    @Test
    void rejectsOversizedMessageWithoutCallingHandler() throws Exception {
        var called = new AtomicBoolean();
        var config = configBuilder().maxMessageBytes(32).build();
        try (var server = new MailHatchServer(config, ignored -> called.set(true)).start();
             var smtp = Client.connect(server.port())) {
            smtp.read();
            smtp.command("EHLO test.example", "250");
            smtp.command("MAIL FROM:<sender@example.org>", "250");
            smtp.command("RCPT TO:<receiver@example.net>", "250");
            smtp.command("DATA", "354");
            smtp.write("Subject: far too large for this configured server");
            smtp.write("");
            smtp.write("body");
            smtp.write(".");
            assertThat(smtp.read()).startsWith("552");
            assertThat(called).isFalse();
        }
    }

    @Test
    void handlerFailureIsTemporarySoSenderCanRetry() throws Exception {
        var config = configBuilder().build();
        try (var server = new MailHatchServer(config, ignored -> { throw new Exception("database unavailable"); }).start();
             var smtp = Client.connect(server.port())) {
            smtp.read();
            smtp.command("EHLO test.example", "250");
            smtp.command("MAIL FROM:<sender@example.org>", "250");
            smtp.command("RCPT TO:<receiver@example.net>", "250");
            smtp.command("DATA", "354");
            smtp.write("From: sender@example.org");
            smtp.write("To: receiver@example.net");
            smtp.write("Subject: retry me");
            smtp.write("");
            smtp.write("body");
            smtp.write(".");
            assertThat(smtp.read()).startsWith("451");
        }
    }

    private static MailHatchConfig.Builder configBuilder() throws Exception {
        return MailHatchConfig.builder()
                .bindAddress(InetAddress.getLoopbackAddress())
                .port(0)
                .hostname("localhost")
                .idleTimeout(Duration.ofSeconds(30));
    }

    private static final class Client implements AutoCloseable {
        private final Socket socket;
        private final BufferedReader reader;
        private final BufferedWriter writer;

        private Client(Socket socket) throws Exception {
            this.socket = socket;
            this.reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.US_ASCII));
            this.writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.US_ASCII));
        }

        static Client connect(int port) throws Exception {
            return new Client(new Socket(InetAddress.getLoopbackAddress(), port));
        }

        void command(String command, String expectedPrefix) throws Exception {
            write(command);
            String response = command.startsWith("EHLO") ? readEhlo() : read();
            assertThat(response).startsWith(expectedPrefix);
        }

        void write(String line) throws Exception {
            writer.write(line);
            writer.write("\r\n");
            writer.flush();
        }

        String read() throws Exception { return reader.readLine(); }

        String readEhlo() throws Exception {
            var response = new StringBuilder();
            String line;
            do {
                line = reader.readLine();
                if (response.length() > 0) response.append('\n');
                response.append(line);
            } while (line != null && line.startsWith("250-"));
            return response.toString();
        }

        @Override
        public void close() throws Exception { socket.close(); }
    }
}
