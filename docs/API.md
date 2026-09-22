# API and lifecycle

## Server lifecycle

`MailHatchServer` is an `AutoCloseable` listener:

1. Build an immutable `MailHatchConfig`.
2. Construct the server with a `MailHandler` and, optionally, a `RecipientPolicy`.
3. Call `start()` once.
4. In standalone applications, optionally call `awaitShutdown()` to keep the main thread
   alive until the listener closes.
5. Call `close()` during application shutdown.

`start()` binds synchronously. A bind problem fails startup rather than leaving a partially
active server. TLS material is validated by `TlsConfig` and parsed when a TLS connection is
first negotiated. `close()` stops accepting connections, closes the listener, and terminates
its Netty event-loop groups.

`awaitShutdown()` expresses the standalone lifecycle directly. It blocks until another
thread or shutdown path closes the listener and propagates thread interruption normally.

## Handler contract

```java
@FunctionalInterface
public interface MailHandler {
    void handle(InboundMessage message) throws Exception;
}
```

The handler is called once after a complete DATA payload has been received and parsed.
Normal return produces SMTP `250`. A generic exception produces temporary SMTP `451`;
clients may retry, so handlers should be idempotent. Throw `SmtpReplyException` to select
a validated 4xx or 5xx code, enhanced status, and single-line explanation. The application
is responsible for durable storage and deduplication, commonly using `Message-ID` plus
application-specific rules.

Handler calls use `handlerExecutor`. A session pauses reads while its handler runs, so
the client cannot begin a second transaction on that connection prematurely. Different
connections can continue concurrently.

## Recipient policy

Use the three-argument server constructor to validate envelope recipients before DATA:

```java
new MailHatchServer(config, context -> {
    if (!routes.contains(context.recipient())) {
        throw new SmtpReplyException(550, "5.1.1", "Unknown recipient");
    }
}, handler);
```

Returning normally accepts the recipient. `SmtpReplyException` returns the specified
4xx or 5xx response. Any other exception produces `451 4.3.0`, so a temporary policy
backend failure does not cause permanent mail loss. `RecipientContext` contains the SMTP
sender, proposed recipient, recipients already accepted in the transaction, HELO/EHLO,
remote address, and TLS state.

Policies run synchronously on the connection I/O thread. They must be non-blocking and
should consult an in-memory route cache rather than call a database or HTTP endpoint.
The accepted-recipient list is an immutable snapshot.

## Deliberate SMTP replies

`SmtpReplyException` only accepts codes from 400 through 599. The enhanced status class
must match the SMTP code (`4.x.x` or `5.x.x`), and response text must be one non-empty line.
These checks prevent accidental success responses and SMTP response injection.

## Nullability

Internet messages are not guaranteed to contain Subject, Message-ID, Date, text, or HTML.
The corresponding record components may be `null`. Convenience methods such as
`optionalSubject()` and `optionalTextBody()` are provided. Address and attachment lists
are never null.

## Immutability

Records and collections exposed by the API are immutable. Attachment and raw-message
byte arrays are copied both on construction and access. This makes messages safe to pass
across threads, at the cost of an additional copy when byte content is accessed.

## Memory model

One message is buffered in memory per active DATA transaction and parsed into memory.
Set `maxMessageBytes` according to expected concurrency and heap size. MailHatch is aimed
at application email processing, not unbounded multi-gigabyte mail transfer.
