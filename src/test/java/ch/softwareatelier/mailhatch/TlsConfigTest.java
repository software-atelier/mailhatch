package ch.softwareatelier.mailhatch;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TlsConfigTest {
    @Test
    void rejectsMissingPemFilesEarly() {
        assertThatThrownBy(() -> TlsConfig.startTls(
                Path.of("missing-fullchain.pem"), Path.of("missing-private-key.pem")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Certificate chain does not exist");
    }
}
