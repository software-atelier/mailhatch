# Production checklist

## Before launch

- Choose STARTTLS or implicit TLS; do not expose plaintext SMTP unintentionally.
- Set a real SMTP hostname and a certificate covering it.
- Restrict bind address and firewall rules where public SMTP is unnecessary.
- Set `maxMessageBytes`, `maxRecipients`, and `idleTimeout` for the workload.
- Supply a bounded executor or virtual-thread executor appropriate for the handler.
- Persist accepted work durably before the handler returns.
- Make the handler idempotent because SMTP senders retry temporary failures.
- Add application metrics for accepted, rejected, failed, and processing duration.
- Avoid logging raw bodies, credentials, or sensitive headers.
- Verify Certbot renewal and private-key permissions.

## Internet-facing operation

MailHatch is intentionally a processing endpoint, not a complete mail transfer agent.
For hostile internet traffic, consider placing a mature MTA or SMTP security gateway in
front for connection throttling, reputation controls, greylisting, DKIM/SPF/DMARC policy,
antivirus, and recipient validation. The proxy can forward accepted traffic to MailHatch
over an isolated network.

## Graceful shutdown

Close the server from the application's shutdown hook. Coordinate shutdown with the
handler executor so already accepted work is not abandoned. Executor lifecycle is owned
by the embedding application; MailHatch does not close a supplied executor.
