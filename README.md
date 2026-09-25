# MailHatch

<p align="center">
  <img src="assets/mailhatch-mascot.png" width="300" alt="MailHatch mascot: a cheerful golden hatchling emerging from a blue envelope shell">
</p>

[![CI](https://github.com/software-atelier/mailhatch/actions/workflows/ci.yml/badge.svg)](https://github.com/software-atelier/mailhatch/actions/workflows/ci.yml)
[![Java 21](https://img.shields.io/badge/Java-21-blue.svg)](https://openjdk.org/projects/jdk/21/)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)

MailHatch is an embeddable Java SMTP server for applications that process inbound email.
It accepts a message over SMTP, parses its RFC 5322/MIME content, and hands a compact,
immutable `InboundMessage` to your code. MailHatch deliberately does **not** send or
relay email.

## Highlights

- Plain SMTP, STARTTLS, and implicit TLS (SMTPS)
- Direct support for PEM certificate chains and keys from Let's Encrypt/Certbot
- Automatic certificate reload after Certbot renewals; no server restart required
- Parsed SMTP envelope, headers, addresses, metadata, text and HTML bodies
- Decoded attachments plus the original raw message
- Configurable size, recipient, and idle-time limits
- Application-defined recipient validation and deliberate 4xx/5xx replies
- Handler execution outside Netty's I/O event loop
- No application framework or dependency-injection container required
- Java 21, Maven, Apache 2.0

## Quick start

Once version 0.3.1 is available from
[Maven Central](https://central.sonatype.com/artifact/ch.softwareatelier/mailhatch), add
MailHatch to your Maven project:

```xml
<dependency>
  <groupId>ch.softwareatelier</groupId>
  <artifactId>mailhatch</artifactId>
  <version>0.3.1</version>
</dependency>
```

Until then, clone the repository and install the current checkout with `mvn install`.

Create and start a listener:

```java
import ch.softwareatelier.mailhatch.MailHatchConfig;
import ch.softwareatelier.mailhatch.MailHatchServer;

var config = MailHatchConfig.builder()
    .port(2525)
    .hostname("mail.example.com")
    .build();

try (var server = new MailHatchServer(config, message -> {
    System.out.println("Envelope sender: " + message.envelope().mailFrom());
    System.out.println("Subject: " + message.subject());
    System.out.println("Text: " + message.textBody());
    message.attachments().forEach(a ->
        System.out.println(a.filename() + " (" + a.content().length + " bytes)"));
}).start()) {
    server.awaitShutdown();
}
```

The handler is the application boundary. MailHatch contains no business-specific
handler implementation.

Validate an envelope recipient before accepting message data:

```java
var server = new MailHatchServer(config, context -> {
    if (!routeCache.contains(context.recipient())) {
        throw new SmtpReplyException(550, "5.1.1", "Unknown recipient");
    }
}, message -> {
    try {
        application.store(message);
    } catch (ApplicationUnavailableException unavailable) {
        throw new SmtpReplyException(451, "4.3.0", "Storage temporarily unavailable");
    }
});
```

`RecipientPolicy` runs during `RCPT TO` on the connection I/O thread and must not block;
use a local cache for remotely managed routes. A normal handler exception still returns
generic `451`. Throw `SmtpReplyException` when the application needs an explicit 4xx or
5xx reply after `DATA`.

## STARTTLS with Let's Encrypt

```java
import ch.softwareatelier.mailhatch.TlsConfig;

var config = MailHatchConfig.builder()
    .port(25)
    .hostname("mail.example.com")
    .tls(TlsConfig.startTls(
        Path.of("/etc/letsencrypt/live/mail.example.com/fullchain.pem"),
        Path.of("/etc/letsencrypt/live/mail.example.com/privkey.pem")))
    .build();
```

`TlsConfig.startTls(...)` requires encryption before `MAIL FROM`. Use the full
constructor and set `requireTls` to `false` only if you intentionally want opportunistic
TLS. For port 465 use `TlsConfig.implicit(...)`.

MailHatch checks the PEM files' modification timestamps for every new TLS session and
rebuilds its TLS context when Certbot updates them. See [TLS and Let's Encrypt](docs/TLS.md)
for DNS, firewall, Certbot, permissions, and renewal guidance.

## Message model

`InboundMessage` provides:

- `envelope()` – SMTP sender, recipients, EHLO/HELO name, remote IP, receipt time, TLS flag
- `headers()` – all header fields with case-insensitive lookup and repeated values preserved
- `subject()`, `messageId()`, `sentAt()`
- `from()`, `to()`, `cc()` – parsed message-header addresses
- `textBody()`, `htmlBody()` – first matching body of each type
- `attachments()` – filename, media type, disposition, Content-ID, decoded bytes
- `rawMessage()` – defensive copy of the original message bytes

The SMTP envelope and the `From`/`To` headers are intentionally separate. They can
legitimately differ and should not be treated as interchangeable security identities.

## Configuration

| Setting | Default | Purpose |
|---|---:|---|
| `bindAddress` | `0.0.0.0` | Listener address |
| `port` | `2525` | Listener port; `0` selects a free test port |
| `hostname` | `localhost` | SMTP banner and EHLO response |
| `maxMessageBytes` | 25 MiB | Maximum DATA payload held in memory |
| `maxRecipients` | 100 | Maximum envelope recipients per transaction |
| `idleTimeout` | 5 minutes | Inactive connection timeout |
| `tls` | disabled | Plain, STARTTLS, or implicit TLS |
| `handlerExecutor` | common pool | Executor used for handler calls |

For blocking handlers, use virtual threads:

```java
try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
    var config = MailHatchConfig.builder()
        .handlerExecutor(executor)
        .build();
    // start and use server
}
```

## SMTP behavior

MailHatch implements the inbound subset needed by processing applications: EHLO/HELO,
MAIL, RCPT, DATA, RSET, NOOP, QUIT, SIZE, 8BITMIME, SMTPUTF8, PIPELINING, and STARTTLS.
If parsing or the handler fails, the SMTP transaction is rejected instead of silently
losing the message. A handler exception returns a temporary `451`, allowing a conforming
sender to retry. A `RecipientPolicy` can reject during `RCPT TO`; a policy or handler can
throw `SmtpReplyException` for a deliberate, validated 4xx or 5xx response.

MailHatch has no outbound delivery path and therefore cannot act as an open relay.
Authentication, persistence, deduplication, and retry semantics belong to the embedding
application or an SMTP proxy in front of it. MailHatch provides the recipient-policy hook,
but the application supplies and refreshes the actual routing data.

## Build and test

```bash
mvn clean verify
mvn javadoc:javadoc
```

The integration tests start real listeners on ephemeral ports and send MIME messages
through Angus Mail using plain SMTP, STARTTLS, and SMTPS.

## Documentation

- [API and lifecycle](docs/API.md)
- [Architecture and processing flow](docs/ARCHITECTURE.md)
- [TLS and Let's Encrypt](docs/TLS.md)
- [Production checklist](docs/PRODUCTION.md)
- [Release process](docs/RELEASING.md)
- [Security policy](SECURITY.md)
- [Contributing](CONTRIBUTING.md)

## Status

`0.x` is an initial API line. Review the changelog before upgrading until `1.0.0`.

## License

Apache License 2.0. Copyright 2026 Tobias Kamber.
