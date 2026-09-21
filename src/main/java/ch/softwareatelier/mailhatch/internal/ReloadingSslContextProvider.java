package ch.softwareatelier.mailhatch.internal;

import ch.softwareatelier.mailhatch.TlsConfig;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.attribute.FileTime;

public final class ReloadingSslContextProvider {
    private static final Logger LOG = LoggerFactory.getLogger(ReloadingSslContextProvider.class);
    private final TlsConfig config;
    private volatile Snapshot snapshot;

    public ReloadingSslContextProvider(TlsConfig config) {
        this.config = config;
    }

    public SslContext current() throws IOException {
        FileTime certificateTime = Files.getLastModifiedTime(config.certificateChain());
        FileTime keyTime = Files.getLastModifiedTime(config.privateKey());
        Snapshot current = snapshot;
        if (current == null || !current.matches(certificateTime, keyTime)) {
            synchronized (this) {
                current = snapshot;
                if (current == null || !current.matches(certificateTime, keyTime)) {
                    try {
                        SslContext context = SslContextBuilder
                                .forServer(config.certificateChain().toFile(), config.privateKey().toFile())
                                .build();
                        snapshot = current = new Snapshot(certificateTime, keyTime, context);
                    } catch (IOException reloadFailure) {
                        if (current == null) throw reloadFailure;
                        LOG.warn("TLS certificate reload failed; keeping the last known good certificate", reloadFailure);
                    }
                }
            }
        }
        return current.context();
    }

    private record Snapshot(FileTime certificateTime, FileTime keyTime, SslContext context) {
        boolean matches(FileTime certificate, FileTime key) {
            return certificateTime.equals(certificate) && keyTime.equals(key);
        }
    }
}
