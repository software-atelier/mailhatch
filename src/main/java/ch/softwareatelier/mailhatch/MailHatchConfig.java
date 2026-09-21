package ch.softwareatelier.mailhatch;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;

/**
 * Immutable listener and safety configuration.
 * @param bindAddress local listener address
 * @param port local port, or zero for an ephemeral port
 * @param hostname SMTP banner hostname
 * @param maxMessageBytes maximum DATA payload size
 * @param maxRecipients maximum recipients per transaction
 * @param idleTimeout inactive connection timeout
 * @param tls TLS settings
 * @param handlerExecutor executor for application handlers
 */
public record MailHatchConfig(InetAddress bindAddress, int port, String hostname,
                              long maxMessageBytes, int maxRecipients,
                              Duration idleTimeout, TlsConfig tls,
                              Executor handlerExecutor) {
    public MailHatchConfig {
        Objects.requireNonNull(bindAddress, "bindAddress");
        Objects.requireNonNull(hostname, "hostname");
        Objects.requireNonNull(idleTimeout, "idleTimeout");
        Objects.requireNonNull(tls, "tls");
        Objects.requireNonNull(handlerExecutor, "handlerExecutor");
        if (port < 0 || port > 65_535) throw new IllegalArgumentException("port must be 0..65535");
        if (maxMessageBytes < 1) throw new IllegalArgumentException("maxMessageBytes must be positive");
        if (maxRecipients < 1) throw new IllegalArgumentException("maxRecipients must be positive");
        if (idleTimeout.isNegative() || idleTimeout.isZero()) {
            throw new IllegalArgumentException("idleTimeout must be positive");
        }
    }

    /** @return a builder with safe application-oriented defaults */
    public static Builder builder() { return new Builder(); }

    /** Fluent builder for {@link MailHatchConfig}. */
    public static final class Builder {
        private InetAddress bindAddress;
        private int port = 2525;
        private String hostname = "localhost";
        private long maxMessageBytes = 25L * 1024 * 1024;
        private int maxRecipients = 100;
        private Duration idleTimeout = Duration.ofMinutes(5);
        private TlsConfig tls = TlsConfig.disabled();
        private Executor handlerExecutor = ForkJoinPool.commonPool();

        private Builder() {
            try { bindAddress = InetAddress.getByName("0.0.0.0"); }
            catch (UnknownHostException impossible) { throw new IllegalStateException(impossible); }
        }

        /** @param value local address @return this builder */
        public Builder bindAddress(InetAddress value) { bindAddress = value; return this; }
        /** @param value local port @return this builder */
        public Builder port(int value) { port = value; return this; }
        /** @param value SMTP hostname @return this builder */
        public Builder hostname(String value) { hostname = value; return this; }
        /** @param value maximum message bytes @return this builder */
        public Builder maxMessageBytes(long value) { maxMessageBytes = value; return this; }
        /** @param value maximum recipients @return this builder */
        public Builder maxRecipients(int value) { maxRecipients = value; return this; }
        /** @param value idle timeout @return this builder */
        public Builder idleTimeout(Duration value) { idleTimeout = value; return this; }
        /** @param value TLS settings @return this builder */
        public Builder tls(TlsConfig value) { tls = value; return this; }
        /** @param value handler executor @return this builder */
        public Builder handlerExecutor(Executor value) { handlerExecutor = value; return this; }

        /** @return validated immutable configuration */
        public MailHatchConfig build() {
            return new MailHatchConfig(bindAddress, port, hostname, maxMessageBytes,
                    maxRecipients, idleTimeout, tls, handlerExecutor);
        }
    }
}
