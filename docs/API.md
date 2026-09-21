# API and lifecycle

## Server lifecycle

`MailHatchServer` is an `AutoCloseable` listener:

1. Build an immutable `MailHatchConfig`.
2. Construct the server with a `MailHandler`.
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
Normal return produces SMTP `250`. An exception produces temporary SMTP `451`; clients
may retry, so handlers should be idempotent. The application is responsible for durable
storage and deduplication, commonly using `Message-ID` plus application-specific rules.

Handler calls use `handlerExecutor`. A session pauses reads while its handler runs, so
the client cannot begin a second transaction on that connection prematurely. Different
connections can continue concurrently.

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
