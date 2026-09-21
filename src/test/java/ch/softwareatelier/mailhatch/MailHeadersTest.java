package ch.softwareatelier.mailhatch;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MailHeadersTest {
    @Test
    void headerNamesAreCaseInsensitiveAndValuesRemainOrdered() {
        var builder = new MailHeaders.Builder();
        builder.add("Received", "first");
        builder.add("received", "second");
        var headers = builder.build();

        assertThat(headers.first("RECEIVED")).contains("first");
        assertThat(headers.all("Received")).containsExactly("first", "second");
    }
}
