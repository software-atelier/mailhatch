# Architecture

```text
SMTP client
    |
    | TCP / STARTTLS / TLS
    v
Netty listener
    |
    | SMTP state machine + limits
    v
RFC 5322 bytes
    |
    | Angus Mail MIME parser
    v
immutable InboundMessage
    |
    | configured Executor
    v
application MailHandler
```

## Packages

- `ch.softwareatelier.mailhatch` is the public API.
- `ch.softwareatelier.mailhatch.internal` contains protocol, MIME, and TLS implementation.

Applications should not import the internal package; it may change between minor versions.

## SMTP session state

Each TCP connection owns one state machine. It records the greeting, reverse path,
forward paths, TLS state, and current DATA buffer. `RSET` and successful or failed
delivery clear transaction state while preserving the connection greeting.

The implementation uses Netty line framing. SMTP dot-stuffing is removed during DATA and
CRLF line endings are retained in the reconstructed raw message. The message-size limit
is enforced while receiving; excess data is discarded until the terminating dot so the
protocol remains synchronized.

## MIME parsing

Angus Mail parses headers and recursive multiparts. The first `text/plain` and first
`text/html` parts become convenient body fields. File-named, attachment-disposition, and
non-text leaf parts become attachments. The complete raw message remains available for
specialized parsing or archival.

## TLS reload

MailHatch caches a Netty `SslContext` with the certificate and key modification times.
Each new TLS negotiation compares those times. Changed PEM files trigger an atomic
context rebuild; existing TLS sessions continue on their original context.
