package ch.softwareatelier.mailhatch;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

/**
 * PEM certificate configuration, compatible with Certbot/Let's Encrypt output.
 * @param mode listener TLS mode
 * @param certificateChain PEM certificate chain, or {@code null} when disabled
 * @param privateKey PEM private key, or {@code null} when disabled
 * @param requireTls whether STARTTLS must precede a mail transaction
 */
public record TlsConfig(TlsMode mode, Path certificateChain, Path privateKey,
                        boolean requireTls) {
    public TlsConfig {
        Objects.requireNonNull(mode, "mode");
        if (mode == TlsMode.DISABLED) {
            certificateChain = null;
            privateKey = null;
            requireTls = false;
        } else {
            Objects.requireNonNull(certificateChain, "certificateChain");
            Objects.requireNonNull(privateKey, "privateKey");
            if (!Files.isRegularFile(certificateChain)) {
                throw new IllegalArgumentException("Certificate chain does not exist: " + certificateChain);
            }
            if (!Files.isRegularFile(privateKey)) {
                throw new IllegalArgumentException("Private key does not exist: " + privateKey);
            }
        }
    }

    /** @return plaintext SMTP configuration */
    public static TlsConfig disabled() {
        return new TlsConfig(TlsMode.DISABLED, null, null, false);
    }

    /** @param fullchainPem certificate chain @param privateKeyPem private key @return required STARTTLS configuration */
    public static TlsConfig startTls(Path fullchainPem, Path privateKeyPem) {
        return new TlsConfig(TlsMode.STARTTLS, fullchainPem, privateKeyPem, true);
    }

    /** @param fullchainPem certificate chain @param privateKeyPem private key @return implicit TLS configuration */
    public static TlsConfig implicit(Path fullchainPem, Path privateKeyPem) {
        return new TlsConfig(TlsMode.IMPLICIT, fullchainPem, privateKeyPem, true);
    }
}
