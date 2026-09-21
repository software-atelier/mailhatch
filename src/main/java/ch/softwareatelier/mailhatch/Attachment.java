package ch.softwareatelier.mailhatch;

import java.util.Arrays;
import java.util.Optional;

/**
 * An attachment extracted from the MIME tree.
 * @param filename decoded filename, or {@code null}
 * @param contentType MIME Content-Type value
 * @param disposition MIME disposition, or {@code null}
 * @param contentId Content-ID value, or {@code null}
 * @param content decoded attachment bytes
 */
public record Attachment(String filename, String contentType, String disposition,
                         String contentId, byte[] content) {
    public Attachment {
        content = content == null ? new byte[0] : Arrays.copyOf(content, content.length);
    }

    @Override
    public byte[] content() {
        return Arrays.copyOf(content, content.length);
    }

    /** @return the filename when the part declares one */
    public Optional<String> optionalFilename() { return Optional.ofNullable(filename); }
    /** @return the Content-ID when the part declares one */
    public Optional<String> optionalContentId() { return Optional.ofNullable(contentId); }
}
