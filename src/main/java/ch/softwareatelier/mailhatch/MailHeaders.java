package ch.softwareatelier.mailhatch;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/** Immutable, case-insensitive view of all RFC 5322 header fields. */
public final class MailHeaders {
    private final Map<String, List<String>> values;

    MailHeaders(Map<String, List<String>> input) {
        var copy = new LinkedHashMap<String, List<String>>();
        input.forEach((key, value) -> copy.put(key.toLowerCase(Locale.ROOT), List.copyOf(value)));
        values = Collections.unmodifiableMap(copy);
    }

    /** @param name case-insensitive header name @return the first value, if present */
    public Optional<String> first(String name) {
        var entries = all(name);
        return entries.isEmpty() ? Optional.empty() : Optional.of(entries.getFirst());
    }

    /** @param name case-insensitive header name @return all values in message order */
    public List<String> all(String name) {
        return values.getOrDefault(name.toLowerCase(Locale.ROOT), List.of());
    }

    /** @return all values keyed by lowercase header name */
    public Map<String, List<String>> asMap() { return values; }

    /** Mutable construction helper used by parsers. */
    public static final class Builder {
        private final Map<String, List<String>> values = new LinkedHashMap<>();
        /** @param name header name @param value unfolded header value */
        public void add(String name, String value) {
            values.computeIfAbsent(name.toLowerCase(Locale.ROOT), ignored -> new ArrayList<>()).add(value);
        }
        /** @return an immutable header collection */
        public MailHeaders build() { return new MailHeaders(values); }
    }
}
